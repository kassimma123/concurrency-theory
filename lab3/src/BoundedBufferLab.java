import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BoundedBufferLab {
    static final class BoundedBuffer<T> {
        private final Object[] elements;
        private int head;
        private int tail;
        private int size;
        private boolean closed;

        BoundedBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            elements = new Object[capacity];
        }

        public synchronized void put(T item) throws InterruptedException {
            Objects.requireNonNull(item, "item");

            // TODO: wait in a while loop while full and not closed;
            // reject a closed buffer; insert at tail; update tail and size;
            // finally notifyAll().
            throw new UnsupportedOperationException("TODO: put");
        }

        public synchronized Optional<T> take() throws InterruptedException {
            // TODO: wait in a while loop while empty and not closed;
            // return Optional.empty() when closed and empty;
            // otherwise remove at head, clear the slot, update state,
            // notifyAll(), and return the value.
            throw new UnsupportedOperationException("TODO: take");
        }

        public synchronized void close() {
            closed = true;
            notifyAll();
        }

        public synchronized int size() {
            return size;
        }

        public int capacity() {
            return elements.length; // final field; array length never changes
        }
    }

    private static Thread producer(BoundedBuffer<Integer> buffer,
                                   int producerId,
                                   int itemCount) {
        return new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    buffer.put(producerId * itemCount + i);
                    if ((i & 255) == 0) {
                        Thread.sleep(1); // outside the monitor
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer-" + producerId);
    }

    private static Thread consumer(BoundedBuffer<Integer> buffer,
                                   int consumerId,
                                   long[] counts,
                                   long[] sums) {
        return new Thread(() -> {
            try {
                while (true) {
                    Optional<Integer> next = buffer.take();
                    if (next.isEmpty()) {
                        return;
                    }
                    counts[consumerId]++;
                    sums[consumerId] += next.get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer-" + consumerId);
    }

    public static void main(String[] args) throws InterruptedException {
        final int producerCount = 3;
        final int consumerCount = 2;
        final int itemsPerProducer = 10_000;
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(5);

        long[] counts = new long[consumerCount];
        long[] sums = new long[consumerCount];
        List<Thread> producers = new ArrayList<>();
        List<Thread> consumers = new ArrayList<>();

        for (int i = 0; i < consumerCount; i++) {
            consumers.add(consumer(buffer, i, counts, sums));
        }
        for (int i = 0; i < producerCount; i++) {
            producers.add(producer(buffer, i, itemsPerProducer));
        }

        consumers.forEach(Thread::start);
        producers.forEach(Thread::start);

        try {
            for (Thread thread : producers) {
                thread.join();
            }
            buffer.close();
            for (Thread thread : consumers) {
                thread.join();
            }
        } catch (InterruptedException e) {
            producers.forEach(Thread::interrupt);
            consumers.forEach(Thread::interrupt);
            throw e;
        } finally {
            // Takze przerwanie watku koordynujacego budzi konsumentow.
            buffer.close();
        }

        long actualCount = 0;
        long actualSum = 0;
        for (int i = 0; i < consumerCount; i++) {
            actualCount += counts[i];
            actualSum += sums[i];
        }

        long expectedCount = (long) producerCount * itemsPerProducer;
        long expectedSum = expectedCount * (expectedCount - 1) / 2;
        System.out.printf("count=%d, sum=%d%n", actualCount, actualSum);
        if (actualCount != expectedCount || actualSum != expectedSum) {
            throw new AssertionError("count or checksum mismatch");
        }
    }
}