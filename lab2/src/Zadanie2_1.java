import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class UnsafeCounter {
    private int value = 0;

    public void increment() {
        value++;
    }

    public int get() {
        return value;
    }
}

public class Zadanie2_1 {
    private static final int N = 4;          // liczba wątków
    private static final int K = 250_000;    // inkrementacji na wątek
    private static final int EXPECTED = N * K; // 1 000 000
    private static final int PROBY = 10;

    public static void main(String[] args) throws InterruptedException {
        List<Integer> wyniki = new ArrayList<>();
        int bledneProby = 0;

        for (int p = 1; p <= PROBY; p++) {
            // W każdym teście tworzymy nowy licznik
            UnsafeCounter counter = new UnsafeCounter();

            Thread[] watki = new Thread[N];
            for (int i = 0; i < N; i++) {
                watki[i] = new Thread(() -> {
                    for (int j = 0; j < K; j++) {
                        counter.increment();
                    }
                });
            }

            // Uruchamiamy wszystkie 4 wątki
            for (Thread t : watki) {
                t.start();
            }

            // Czekamy na zakończenie wszystkich wątków
            for (Thread t : watki) {
                t.join();
            }

            int wynik = counter.get();
            wyniki.add(wynik);
            if (wynik != EXPECTED) {
                bledneProby++;
            }
            System.out.printf("Próba %2d: wynik = %d%n", p, wynik);
        }

        int min = Collections.min(wyniki);
        int max = Collections.max(wyniki);

        System.out.println("----------------------------------------");
        System.out.println("Oczekiwano: " + EXPECTED);
        System.out.println("Minimum:    " + min);
        System.out.println("Maksimum:   " + max);
        System.out.println("Liczba prób różnych od oczekiwanej: " + bledneProby + " / " + PROBY);
    }
}