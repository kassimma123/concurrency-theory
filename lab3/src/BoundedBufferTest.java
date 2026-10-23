import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zadanie 4: Testy stanow granicznych i przerwan.
 *
 * 8 deterministycznych testow wymaganych przez specyfikacje laba:
 *  1. Sekwencyjny deterministyczny test FIFO dla C=2 z zawinieciem indeksow
 *  2. Konsument czeka na pustym buforze, po czym close() go budzi i dostaje empty
 *  3. Po close() konsument oproznia bufor i dopiero potem dostaje Optional.empty()
 *  4. put() po close() rzuca IllegalStateException, a wielokrotne close() nie szkodzi
 *  5. Producent czeka na pelnym buforze, close() go budzi i put() rzuca IllegalStateException
 *  6. Producent zablokowany na pelnym buforze zostaje przerwany i konczy z odtworzonym znacznikiem przerwania
 *  7. Konsument zablokowany na pustym buforze zostaje przerwany analogicznie
 *  8. Caly eksperyment konczy sie w ustalonym limicie czasu (timeout cleanup z join(timeout) i isAlive())
 */
public class BoundedBufferTest {

    private static final long TIMEOUT_MS = 3000;

    public static void main(String[] args) {
        System.out.println("=== Teoria Wspolbieznosci - Cwiczenie 3: Zadanie 4 (Testy) ===");
        runTest("Test 1: Sekwencyjny FIFO (C=2) z zawinieciem indeksow", BoundedBufferTest::test1_FifoWrapAround);
        runTest("Test 2: Konsument czeka na pustym buforze, close() go budzi", BoundedBufferTest::test2_ConsumerWaitAndClose);
        runTest("Test 3: Po close() bufor jest najpierw oprozniany, potem empty", BoundedBufferTest::test3_DrainRemainingAfterClose);
        runTest("Test 4: put() po close() rzuca IllegalStateException, wielokrotne close() bezpieczne", BoundedBufferTest::test4_PutAfterCloseAndIdempotentClose);
        runTest("Test 5: Producent czeka na pelnym buforze, close() powoduje IllegalStateException", BoundedBufferTest::test5_ProducerWaitAndClose);
        runTest("Test 6: Producent zablokowany na wait() zostaje przerwany i odtwarza znacznik", BoundedBufferTest::test6_ProducerInterruptRestoresFlag);
        runTest("Test 7: Konsument zablokowany na wait() zostaje przerwany i odtwarza znacznik", BoundedBufferTest::test7_ConsumerInterruptRestoresFlag);
        runTest("Test 8: Wykrywanie przekroczenia limitu czasu i bezpieczne sprzatanie watkow", BoundedBufferTest::test8_TimeoutAndCleanShutdown);
        System.out.printf("%nWszystkie testy z Zadania 4 zakonczone sukcesem!%n");
    }

    private static void runTest(String name, TestCase test) {
        System.out.print(name + "... ");
        try {
            test.execute();
            System.out.println("PASSED");
        } catch (Throwable t) {
            System.out.println("FAILED!");
            t.printStackTrace(System.out);
            System.exit(1);
        }
    }

    @FunctionalInterface
    interface TestCase {
        void execute() throws Exception;
    }

    /**
     * Test 1: sekwencyjny, deterministyczny test FIFO dla pojemnosci C = 2, z zawinieciem indeksow:
     * po put(1) i put(2) pierwszy wynik ma wynosic 1; nastepnie po put(3) kolejne wyniki maja wynosic 2 i 3.
     */
    static void test1_FifoWrapAround() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);
        buffer.put(1);
        buffer.put(2);
        // Sprawdzenie stanu poczatkowego
        assertEquals(2, buffer.size(), "Rozmiar po 2 put()");
        assertEquals(0, buffer.head(), "Head przed pierwszym take()");
        assertEquals(0, buffer.tail(), "Tail przed pierwszym take() (zawiniecie modulo 2)");

        Optional<Integer> first = buffer.take();
        assertTrue(first.isPresent() && first.get() == 1, "Pierwszy pobrany element musi byc 1");
        assertEquals(1, buffer.head(), "Head po pobraniu 1");

        // Zawiniecie tail
        buffer.put(3);
        assertEquals(2, buffer.size(), "Rozmiar po put(3)");
        assertEquals(1, buffer.tail(), "Tail po put(3)");

        Optional<Integer> second = buffer.take();
        assertTrue(second.isPresent() && second.get() == 2, "Drugi element musi byc 2");

        Optional<Integer> third = buffer.take();
        assertTrue(third.isPresent() && third.get() == 3, "Trzeci element musi byc 3");

        assertEquals(0, buffer.size(), "Bufor musi byc pusty");
    }

    /**
     * Test 2: konsument czeka na pustym buforze, po czym close() go budzi i zwraca Optional.empty().
     */
    static void test2_ConsumerWaitAndClose() throws Exception {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(2);
        CountDownLatch consumerWaitingLatch = new CountDownLatch(1);
        buffer.setHooks(null, consumerWaitingLatch::countDown);

        AtomicReference<Optional<String>> result = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            try {
                result.set(buffer.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        assertTrue(consumerWaitingLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "Konsument nie wszedl do wait()");

        // Wolamy close() na buforze
        buffer.close();

        joinWithTimeout(consumer, TIMEOUT_MS);
        assertFalse(consumer.isAlive(), "Watek konsumenta powinien zakonczyc prace po close()");
        assertNotNull(result.get(), "Konsument powinien zwrocic wartosc");
        assertTrue(result.get().isEmpty(), "Wynik take() po close() na pustym buforze to Optional.empty()");
    }

    /**
     * Test 3: po close() konsument oproznia bufor i dopiero potem dostaje koniec strumienia (Optional.empty()).
     */
    static void test3_DrainRemainingAfterClose() throws Exception {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(2);
        buffer.put("A");
        buffer.put("B");

        // Zamykamy bufor, gdy znajduja sie w nim elementy
        buffer.close();
        assertTrue(buffer.isClosed(), "Bufor powinien byc oznaczony jako closed");

        // Pobranie pozostalych elementow
        Optional<String> first = buffer.take();
        assertTrue(first.isPresent() && "A".equals(first.get()), "Pierwszy element to A");

        Optional<String> second = buffer.take();
        assertTrue(second.isPresent() && "B".equals(second.get()), "Drugi element to B");

        // Dopiero teraz, gdy pusty, zwraca Optional.empty()
        Optional<String> third = buffer.take();
        assertTrue(third.isEmpty(), "Po oproznieniu zamknietego bufora take() zwraca Optional.empty()");
    }

    /**
     * Test 4: put() po close() jest odrzucane (IllegalStateException), a wielokrotne close() nie szkodzi.
     */
    static void test4_PutAfterCloseAndIdempotentClose() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);
        buffer.put(10);
        buffer.close();
        buffer.close(); // Wielokrotne close() (idempotentnosc)
        assertTrue(buffer.isClosed(), "Bufor powinien byc zamkniety");

        boolean threwExpectedException = false;
        try {
            buffer.put(20);
        } catch (IllegalStateException e) {
            threwExpectedException = true;
        }
        assertTrue(threwExpectedException, "put() na zamknietym buforze musi rzucic IllegalStateException");

        // null jest odrzucany NPE niezaleznie od stanu bufora
        boolean threwNpe = false;
        try {
            buffer.put(null);
        } catch (NullPointerException e) {
            threwNpe = true;
        }
        assertTrue(threwNpe, "put(null) musi rzucic NullPointerException");
    }

    /**
     * Test 5: producent czeka na pelnym buforze, po czym close() go budzi,
     * a oczekujace put() konczy sie IllegalStateException.
     */
    static void test5_ProducerWaitAndClose() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(1);
        buffer.put(100); // Pelny bufor

        CountDownLatch producerWaitingLatch = new CountDownLatch(1);
        buffer.setHooks(producerWaitingLatch::countDown, null);

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                buffer.put(200);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });

        producer.start();
        assertTrue(producerWaitingLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "Producent nie wszedl do wait()");

        // Zamykamy bufor
        buffer.close();

        joinWithTimeout(producer, TIMEOUT_MS);
        assertFalse(producer.isAlive(), "Producent powinien zakonczyc prace po obudzeniu przez close()");
        assertTrue(thrown.get() instanceof IllegalStateException, "Oczekujace put() po close() musi rzucic IllegalStateException");
    }

    /**
     * Test 6: producent zablokowany na pelnym buforze zostaje przerwany
     * i konczy dzialanie z odtworzonym znacznikiem przerwania.
     */
    static void test6_ProducerInterruptRestoresFlag() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(1);
        buffer.put(42);

        CountDownLatch waitEnteredLatch = new CountDownLatch(1);
        buffer.setHooks(waitEnteredLatch::countDown, null);

        AtomicBoolean threadFinishedWithInterruptedFlag = new AtomicBoolean(false);
        Thread producer = new Thread(() -> {
            try {
                buffer.put(99);
            } catch (InterruptedException e) {
                // Zgodnie z Listingiem 2 specyfikacji:
                Thread.currentThread().interrupt(); // odtworzenie znacznika
            } finally {
                threadFinishedWithInterruptedFlag.set(Thread.currentThread().isInterrupted());
            }
        });

        producer.start();
        assertTrue(waitEnteredLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "Producent nie wszedl do wait()");

        // Przerwanie watku producenta
        producer.interrupt();

        joinWithTimeout(producer, TIMEOUT_MS);
        assertFalse(producer.isAlive(), "Watek producenta powinien zakonczyc sie po przerwaniu");
        assertTrue(threadFinishedWithInterruptedFlag.get(), "Watek powinien zakonczyc prace z odtworzonym znacznikiem przerwania");
    }

    /**
     * Test 7: konsument zablokowany na pustym buforze zostaje przerwany analogicznie.
     */
    static void test7_ConsumerInterruptRestoresFlag() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(1);

        CountDownLatch waitEnteredLatch = new CountDownLatch(1);
        buffer.setHooks(null, waitEnteredLatch::countDown);

        AtomicBoolean threadFinishedWithInterruptedFlag = new AtomicBoolean(false);
        Thread consumer = new Thread(() -> {
            try {
                buffer.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // odtworzenie znacznika
            } finally {
                threadFinishedWithInterruptedFlag.set(Thread.currentThread().isInterrupted());
            }
        });

        consumer.start();
        assertTrue(waitEnteredLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "Konsument nie wszedl do wait()");

        consumer.interrupt();

        joinWithTimeout(consumer, TIMEOUT_MS);
        assertFalse(consumer.isAlive(), "Watek konsumenta powinien zakonczyc sie po przerwaniu");
        assertTrue(threadFinishedWithInterruptedFlag.get(), "Watek powinien zakonczyc prace z odtworzonym znacznikiem przerwania");
    }

    /**
     * Test 8: caly eksperyment konczy sie w ustalonym limicie czasu (timeout cleanup).
     * Sprawdza poprawnosc procedury czyszczenia zasobow i join(timeout) z weryfikacja isAlive().
     */
    static void test8_TimeoutAndCleanShutdown() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);

        Thread stubbornWorker = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    buffer.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        stubbornWorker.start();

        // Bezpieczne sprzatanie: zamknij bufor, przerwij watek, dolacz z limitem czasu
        buffer.close();
        stubbornWorker.interrupt();
        stubbornWorker.join(TIMEOUT_MS);

        if (stubbornWorker.isAlive()) {
            throw new AssertionError("BLAD: Watek wciaz zyje po limicie czasu join(timeout)!");
        }
    }

    private static void joinWithTimeout(Thread t, long timeoutMs) throws InterruptedException {
        t.join(timeoutMs);
        if (t.isAlive()) {
            t.interrupt();
            t.join(500);
            throw new AssertionError("Timeout! Watek " + t.getName() + " nie zakonczyl sie w czasie " + timeoutMs + " ms.");
        }
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " - oczekiwano: " + expected + ", otrzymano: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("Asertion failed: " + msg);
        }
    }

    private static void assertFalse(boolean condition, String msg) {
        if (condition) {
            throw new AssertionError("Asertion failed (expected false): " + msg);
        }
    }

    private static void assertNotNull(Object obj, String msg) {
        if (obj == null) {
            throw new AssertionError("Asertion failed: " + msg);
        }
    }
}
