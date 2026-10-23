import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zadanie 3: Wiele watkow i zakonczenie obliczen za pomoca protokolu close().
 *
 * Testowane konfiguracje:
 *  - (P=1, C=1, N=10000)
 *  - (P=4, C=1, N=10000)
 *  - (P=1, C=4, N=10000)
 *  - (P=4, C=4, N=10000)
 * dla pojemnosci:
 *  - C = 1
 *  - C = 2
 *  - C = 10 (> liczba producentow)
 */
public class BoundedBufferLab {

    record Config(int producers, int consumers, int itemsPerProducer, int capacity) {}

    public static void main(String[] args) {
        System.out.println("=== Teoria Wspolbieznosci - Cwiczenie 3: Zadanie 3 ===");
        System.out.println("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");

        int[] capacities = {1, 2, 10};
        int[][] threadPairs = {
                {1, 1},
                {4, 1},
                {1, 4},
                {4, 4}
        };
        int itemsPerProducer = 10_000;

        for (int cap : capacities) {
            System.out.printf("%n================ Pojemnosc bufora: %d ================%n", cap);
            for (int[] pair : threadPairs) {
                int p = pair[0];
                int c = pair[1];
                Config config = new Config(p, c, itemsPerProducer, cap);
                runExperiment(config, true); // true = dodaj losowe opoznienia (symulacja pracy poza buforem)
            }
        }
    }

    public static void runExperiment(Config config, boolean withDelays) {
        final int numProducers = config.producers();
        final int numConsumers = config.consumers();
        final int itemsPerProducer = config.itemsPerProducer();
        final int capacity = config.capacity();
        final long totalExpectedItems = (long) numProducers * itemsPerProducer;

        System.out.printf("Test: P=%d, C=%d, el/P=%d, Cap=%d, delays=%b... ",
                numProducers, numConsumers, itemsPerProducer, capacity, withDelays);

        BoundedBuffer<Long> buffer = new BoundedBuffer<>(capacity);

        // Tablice wynikow per-konsument (nie wymagaja synchronizacji: kazdy konsument pisze do wlasnego indeksu)
        long[] counts = new long[numConsumers];
        long[] sums = new long[numConsumers];

        // Listy odebranych elementow dla dokladnej weryfikacji brakow i duplikatow
        @SuppressWarnings("unchecked")
        List<Long>[] consumedItems = new List[numConsumers];
        for (int i = 0; i < numConsumers; i++) {
            consumedItems[i] = new ArrayList<>(Math.max(16, (int) (totalExpectedItems / numConsumers) * 2));
        }

        Thread[] producerThreads = new Thread[numProducers];
        Thread[] consumerThreads = new Thread[numConsumers];

        long expectedSum = 0;
        // Obliczenie oczekiwanej sumy kontrolnej identyfikatorow:
        // unikalny identyfikator = (producerIndex + 1) * 1_000_000_000L + seq
        for (int p = 0; p < numProducers; p++) {
            long base = (p + 1) * 1_000_000_000L;
            for (int seq = 1; seq <= itemsPerProducer; seq++) {
                expectedSum += (base + seq);
            }
        }

        // Tworzenie watkow producentow
        for (int p = 0; p < numProducers; p++) {
            final int prodIndex = p;
            final long base = (prodIndex + 1) * 1_000_000_000L;
            producerThreads[p] = new Thread(() -> {
                try {
                    for (int seq = 1; seq <= itemsPerProducer; seq++) {
                        long item = base + seq;
                        if (withDelays && (seq % 1000 == 0)) {
                            // Krotkie losowe opoznienie poza buforem (max 100 mikrosekund)
                            int delayNanos = ThreadLocalRandom.current().nextInt(100_000);
                            Thread.sleep(0, delayNanos);
                        }
                        buffer.put(item);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Producent-" + prodIndex);
        }

        // Tworzenie watkow konsumentow
        for (int c = 0; c < numConsumers; c++) {
            final int consIndex = c;
            final List<Long> myItems = consumedItems[consIndex];
            consumerThreads[c] = new Thread(() -> {
                long myCount = 0;
                long mySum = 0;
                try {
                    while (true) {
                        Optional<Long> itemOpt = buffer.take();
                        if (itemOpt.isEmpty()) {
                            // Protokol zakonczenia: bufor zamkniety i pusty
                            break;
                        }
                        long val = itemOpt.get();
                        myCount++;
                        mySum += val;
                        myItems.add(val);

                        if (withDelays && (myCount % 1000 == 0)) {
                            // Krotkie losowe opoznienie poza buforem
                            int delayNanos = ThreadLocalRandom.current().nextInt(100_000);
                            Thread.sleep(0, delayNanos);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    counts[consIndex] = myCount;
                    sums[consIndex] = mySum;
                }
            }, "Konsument-" + consIndex);
        }

        long startTime = System.nanoTime();
        try {
            // Start konsumentow i producentow
            for (Thread cThread : consumerThreads) cThread.start();
            for (Thread pThread : producerThreads) pThread.start();

            // Koordynator: czeka na wszystkich producentow
            for (Thread pThread : producerThreads) {
                pThread.join();
            }

            // Dopiero po zakonczeniu WSZYSTKICH producentow koordynator zamyka bufor
            buffer.close();

            // Koordynator: czeka na oproznienie bufora i zakonczenie wszystkich konsumentow
            for (Thread cThread : consumerThreads) {
                cThread.join();
            }
        } catch (InterruptedException e) {
            // W razie przerwania koordynatora przerywamy watki potomne
            for (Thread pThread : producerThreads) pThread.interrupt();
            for (Thread cThread : consumerThreads) cThread.interrupt();
            Thread.currentThread().interrupt();
        } finally {
            // Zapewnienie, ze bufor zostanie zamkniety
            buffer.close();
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Weryfikacja wynikow
        long totalConsumedCount = 0;
        long totalConsumedSum = 0;
        for (int c = 0; c < numConsumers; c++) {
            totalConsumedCount += counts[c];
            totalConsumedSum += sums[c];
        }

        if (totalConsumedCount != totalExpectedItems) {
            throw new AssertionError("BLAD: Oczekiwano " + totalExpectedItems + " elementow, a odebrano " + totalConsumedCount);
        }

        if (totalConsumedSum != expectedSum) {
            throw new AssertionError("BLAD: Niezgodna suma kontrolna! Oczekiwano: " + expectedSum + ", otrzymano: " + totalConsumedSum);
        }

        // Dokladny test unikalnosci i braku duplikatow
        HashSet<Long> uniqueIds = new HashSet<>((int) totalExpectedItems * 2);
        for (int c = 0; c < numConsumers; c++) {
            for (Long id : consumedItems[c]) {
                if (!uniqueIds.add(id)) {
                    throw new AssertionError("BLAD: Wykryto zduplikowany element ID: " + id);
                }
            }
        }

        if (uniqueIds.size() != totalExpectedItems) {
            throw new AssertionError("BLAD: Liczba unikalnych ID (" + uniqueIds.size() + ") rozna od oczekiwanej (" + totalExpectedItems + ")");
        }

        System.out.printf("OK! (%d ms, odebrano %d el., suma zgodna)%n", durationMs, totalConsumedCount);
    }
}
