import java.util.Objects;
import java.util.Optional;

/**
 * Bezpieczny, ograniczony bufor FIFO dla wielu producentów i konsumentów,
 * zaimplementowany z użyciem monitora wewnętrznego obiektu (intrinsic monitor).
 *
 * Niezmienniki stanu:
 *  - 0 <= size <= capacity
 *  - 0 <= head, tail < capacity
 *  - tail = (head + size) % capacity
 *  - dokladnie 'size' kolejnych pol od 'head' do '(head + size - 1) % capacity' jest zajetych (nie-null),
 *    a pozostale pola tablicy to null
 *  - flaga 'closed' jest monotoniczna (moze przejsc wylacznie z false do true).
 */
public class BoundedBuffer<T> {
    private final int capacity;
    private final Object[] buffer;

    private int head = 0;
    private int tail = 0;
    private int size = 0;
    private boolean closed = false;

    // Liczniki rzeczywistych wywolan wait() wymagane w Zadaniu 5
    private long producerWaitCount = 0;
    private long consumerWaitCount = 0;

    // Opcjonalne haki testowe wywolywane pod monitorem tuz przed wait() (Zadanie 4)
    private Runnable beforeProducerWaitHook = null;
    private Runnable beforeConsumerWaitHook = null;

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Pojemnosc musi byc wieksza niz 0: " + capacity);
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    /**
     * Wstawia element do bufora.
     * 1. Odrzuca null (NullPointerException).
     * 2. Dopoki bufor jest pelny i niezamkniety, wykonuje wait().
     * 3. Jesli bufor jest zamkniety, zglasza IllegalStateException.
     * 4. Zapisuje element w tail, przesuwa indeks modulo pojemnosc i zwieksza size.
     * 5. Wykonuje notifyAll().
     */
    public synchronized void put(T x) throws InterruptedException {
        Objects.requireNonNull(x, "Element nie moze byc null");

        while (size == capacity && !closed) {
            producerWaitCount++;
            if (beforeProducerWaitHook != null) {
                beforeProducerWaitHook.run();
            }
            wait();
        }

        if (closed) {
            throw new IllegalStateException("Bufor jest zamkniety - odrzucono operacje put()");
        }

        buffer[tail] = x;
        tail = (tail + 1) % capacity;
        size++;

        notifyAll();
    }

    /**
     * Pobiera element z bufora.
     * 1. Dopoki bufor jest pusty i niezamkniety, wykonuje wait().
     * 2. Jesli bufor jest pusty (a wiec zamkniety), zwraca Optional.empty().
     * 3. Pobiera element z head (z lokalnym @SuppressWarnings("unchecked")), zeruje pole,
     *    przesuwa head modulo pojemnosc i zmniejsza size.
     * 4. Wykonuje notifyAll() i zwraca Optional.of(item).
     */
    public synchronized Optional<T> take() throws InterruptedException {
        while (size == 0 && !closed) {
            consumerWaitCount++;
            if (beforeConsumerWaitHook != null) {
                beforeConsumerWaitHook.run();
            }
            wait();
        }

        if (size == 0) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        T item = (T) buffer[head];
        buffer[head] = null; // pomoc garbage collectorowi i zachowanie niezmiennika pol pustych
        head = (head + 1) % capacity;
        size--;

        notifyAll();
        return Optional.of(item);
    }

    /**
     * Idempotentne zamkniecie bufora.
     * Zaznacza zamkniecie i budzi wszystkie oczekujace watki.
     */
    public synchronized void close() {
        if (!closed) {
            closed = true;
            notifyAll();
        }
    }

    public int capacity() {
        return capacity;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized boolean isFull() {
        return size == capacity;
    }

    public synchronized int head() {
        return head;
    }

    public synchronized int tail() {
        return tail;
    }

    public synchronized long producerWaitCount() {
        return producerWaitCount;
    }

    public synchronized long consumerWaitCount() {
        return consumerWaitCount;
    }

    public synchronized void setHooks(Runnable beforeProducerWait, Runnable beforeConsumerWait) {
        this.beforeProducerWaitHook = beforeProducerWait;
        this.beforeConsumerWaitHook = beforeConsumerWait;
    }
}
