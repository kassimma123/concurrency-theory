import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Zadanie 5 (rozszerzenie): Eksperyment kontrolowany.
 *
 * Mierzy czas wykonania (System.nanoTime()) oraz liczby rzeczywistych wywolan wait()
 * przez producentow i konsumentow dla roznych pojemnosci bufora i proporcji obu grup.
 * Wykonuje kilka powtorzen i raportuje mediane.
 */
public class PerformanceExperiment {

    record Config(int producers, int consumers, int itemsPerProducer, int capacity) {}

    record Result(long durationMs, long producerWaits, long consumerWaits) {}

    public static void main(String[] args) {
        System.out.println("=== Teoria Wspolbieznosci - Cwiczenie 3: Zadanie 5 (Eksperyment) ===");
        System.out.println("Srodowisko: Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("Liczba dostepnych rdzeni CPU: " + Runtime.getRuntime().availableProcessors());

        int repetitions = 5;
        int itemsPerProducer = 20_000;
        int[] capacities = {1, 2, 5, 10, 50};
        int[][] threadPairs = {
                {1, 1},
                {4, 1},
                {1, 4},
                {4, 4}
        };

        System.out.printf("%nLiczba powtorzen na konfiguracje: %d%n", repetitions);
        System.out.printf("Elementow na producenta: %d%n%n", itemsPerProducer);

        // Naglowek tabeli w formacie Markdown dla latwego wklejenia do raportu
        System.out.println("| Pojemnosc | Producenci | Konsumenci | Mediana Czasu [ms] | Mediana wait() Prod | Mediana wait() Kons |");
        System.out.println("|:---------:|:----------:|:----------:|:------------------:|:-------------------:|:-------------------:|");

        for (int cap : capacities) {
            for (int[] pair : threadPairs) {
                int p = pair[0];
                int c = pair[1];
                Config config = new Config(p, c, itemsPerProducer, cap);

                List<Long> durations = new ArrayList<>();
                List<Long> prodWaits = new ArrayList<>();
                List<Long> consWaits = new ArrayList<>();

                // Wykonujemy powtorzenia
                for (int r = 0; r < repetitions; r++) {
                    Result res = runSinglePass(config);
                    durations.add(res.durationMs());
                    prodWaits.add(res.producerWaits());
                    consWaits.add(res.consumerWaits());
                }

                Collections.sort(durations);
                Collections.sort(prodWaits);
                Collections.sort(consWaits);

                long medianDuration = durations.get(durations.size() / 2);
                long medianProdWaits = prodWaits.get(prodWaits.size() / 2);
                long medianConsWaits = consWaits.get(consWaits.size() / 2);

                System.out.printf("| %9d | %10d | %10d | %18d | %19d | %19d |%n",
                        cap, p, c, medianDuration, medianProdWaits, medianConsWaits);
            }
        }
    }

    private static Result runSinglePass(Config config) {
        final int numProducers = config.producers();
        final int numConsumers = config.consumers();
        final int itemsPerProducer = config.itemsPerProducer();
        final int capacity = config.capacity();

        BoundedBuffer<Long> buffer = new BoundedBuffer<>(capacity);

        Thread[] producerThreads = new Thread[numProducers];
        Thread[] consumerThreads = new Thread[numConsumers];

        for (int p = 0; p < numProducers; p++) {
            final int prodIndex = p;
            final long base = (prodIndex + 1) * 1_000_000_000L;
            producerThreads[p] = new Thread(() -> {
                try {
                    for (int seq = 1; seq <= itemsPerProducer; seq++) {
                        buffer.put(base + seq);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "BenchProducer-" + p);
        }

        for (int c = 0; c < numConsumers; c++) {
            consumerThreads[c] = new Thread(() -> {
                try {
                    while (true) {
                        Optional<Long> item = buffer.take();
                        if (item.isEmpty()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "BenchConsumer-" + c);
        }

        long startNs = System.nanoTime();
        try {
            for (Thread c : consumerThreads) c.start();
            for (Thread p : producerThreads) p.start();

            for (Thread p : producerThreads) p.join();
            buffer.close();
            for (Thread c : consumerThreads) c.join();
        } catch (InterruptedException e) {
            for (Thread p : producerThreads) p.interrupt();
            for (Thread c : consumerThreads) c.interrupt();
            Thread.currentThread().interrupt();
        } finally {
            buffer.close();
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        return new Result(durationMs, buffer.producerWaitCount(), buffer.consumerWaitCount());
    }
}
