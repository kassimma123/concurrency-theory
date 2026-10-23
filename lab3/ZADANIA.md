# Teoria Współbieżności – Ćwiczenie 3: Monitory i oczekiwanie na warunek w Javie

## 1. Cel i zakres ćwiczenia
Celem ćwiczenia jest poprawne zastosowanie monitora wewnętrznego (*intrinsic monitor*) obiektu Javy.
Student:
- chroni wspólny stan za pomocą `synchronized`;
- potrafi sformułować predykat, na który czeka wątek;
- używa `wait()` w pętli `while` i rozumie, dlaczego samo `if` jest niepoprawne;
- umieszcza zmianę stanu i `notifyAll()` w tej samej sekcji synchronizowanej;
- propaguje albo świadomie obsługuje `InterruptedException`;
- implementuje bezpieczny, ograniczony bufor FIFO dla wielu producentów i konsumentów;
- kończy obliczenie protokołem `close()`, bez zgadywania liczby operacji wykonywanych przez konsumentów.

> **Wymagania:** JDK 25 LTS, brak zewnętrznych bibliotek, zakaz stosowania `BlockingQueue`, semaforów oraz `Lock`/`Condition` do rozwiązania zadania głównego.

---

## 2. Monitor wewnętrzny obiektu – Teoria i Niezmienniki
Z każdym obiektem Javy związane są:
1. **Blokada wzajemnego wykluczania** – wejście do niestatycznej metody `synchronized` lub bloku `synchronized(this)` zdobywa blokadę instancji. Odblokowanie monitora i jego późniejsze zablokowanie ustanawia relację *happens-before*.
2. **Jeden zbiór wątków oczekujących (*wait-set*)** – używany przez `wait()`, `notify()` i `notifyAll()`.

### Protokół oczekiwania na predykat (Listing 1):
```java
synchronized (monitor) {
    while (!predicate()) {
        monitor.wait();
    }
    // Monitor jest ponownie zajęty, a predykat jest prawdziwy
    changeState();
    monitor.notifyAll();
}
```

### Obsługa przerwań (Listing 2):
```java
try {
    doWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // odtworzenie znacznika przerwania
    return;
}
```

---

## 3. Zadania laboratoryjne

### Zadanie 1: Analiza protokołu
Dla metod `put`, `take` i `close`:
1. **Stan chroniony przez monitor:**
   - `head` (indeks początku kolejki)
   - `tail` (indeks następnego wolnego miejsca)
   - `size` (liczba elementów w buforze)
   - `closed` (flaga zamknięcia bufora)
   - zawartość tablicy `buffer` (pola logiczne od `head` do `tail`)
2. **Predykat pozwalający kontynuować operację:**
   - dla `put(x)`: `size < capacity || closed` (w pętli czekamy dopóki: `size == capacity && !closed`)
   - dla `take()`: `size > 0 || closed` (w pętli czekamy dopóki: `size == 0 && !closed`)
   - dla `close()`: bezwarunkowy (brak oczekiwania, operacja natychmiastowa)
3. **Zmiana stanu wykonywana atomowo:**
   - dla `put(x)`: `buffer[tail] = x; tail = (tail + 1) % capacity; size++;`
   - dla `take()`: `item = buffer[head]; buffer[head] = null; head = (head + 1) % capacity; size--;`
   - dla `close()`: `if (!closed) { closed = true; }`
4. **Wątki, które mogą uzyskać możliwość postępu po tej zmianie:**
   - po `put()`: wątki konsumentów oczekujące na warunku "bufor niepusty" (`size > 0`)
   - po `take()`: wątki producentów oczekujące na warunku "bufor niepełny" (`size < capacity`)
   - po `close()`: WSZYSTKIE wątki oczekujące – zarówno producenci (którzy zostaną obudzeni i rzucą `IllegalStateException`), jak i konsumenci (którzy odbiorą pozostałe dane lub zakończą pracę z `Optional.empty()`).

#### Pytania uzupełniające:
- **Dlaczego w `put` trzeba po przebudzeniu sprawdzić zarówno pełność, jak i zamknięcie?**
  Gdy producent zostaje obudzony przez `notifyAll()`, powodem wybudzenia mogło być zwolnienie miejsca przez konsumenta ALBO zamknięcie bufora przez koordynatora (`close()`). Ponadto zanim obudzony producent odzyska monitor, inny producent mógł już zapełnić bufor. Dlatego w pętli `while (size == capacity && !closed)` sprawdzamy warunek, a po wyjściu sprawdzamy `if (closed)`, aby w razie zamknięcia rzucić `IllegalStateException`.
- **Dlaczego w `take` zamknięty, ale niepusty bufor nadal zwraca dane?**
  Zapewnia to semantykę "drenowania" (drain) – elementy wstawione przez producentów przed zamknięciem bufora nie mogą zostać utracone. Zamknięcie oznacza "brak nowych danych od producentów", a nie "natychmiastowe unieważnienie już wyprodukowanych danych". `Optional.empty()` jest zwracany dopiero, gdy bufor jest ZARÓWNO zamknięty, JAK I pusty (`size == 0 && closed`).

---

### Zadanie 2: Implementacja monitora
Plik: `src/BoundedBuffer.java`
- Odrzucanie `null` z `NullPointerException`.
- Bufor pierścieniowy na tablicy `Object[]` o stałej pojemności $C > 0$.
- Monotoniczna flaga `closed`.
- Pełna implementacja `put`, `take`, `close` pod monitorem `this`.

---

### Zadanie 3: Wiele wątków i zakończenie
Plik: `src/BoundedBufferLab.java`
- Konfiguracje: $(P=1, C=1)$, $(P=4, C=1)$, $(P=1, C=4)$, $(P=4, C=4)$ dla $N=10\,000$ elementów/producent.
- Badane pojemności bufora: $C=1$, $C=2$, $C=10$.
- Generowanie unikalnych ID dla każdego elementu.
- Weryfikacja: liczność, suma kontrolna oraz wykrywanie braków/duplikatów w `HashSet`.
- Koordynacja: `join()` producentów $\to$ `close()` $\to$ `join()` konsumentów.

---

### Zadanie 4: Testy stanów granicznych i przerwań
Plik: `src/BoundedBufferTest.java`
8 powtarzalnych testów deterministycznych z hakami testowymi:
1. Sekwencyjny deterministyczny test FIFO dla $C=2$ z zawinięciem indeksów.
2. Konsument czeka na pustym buforze, po czym `close()` go budzi i zwraca `Optional.empty()`.
3. Po `close()` konsument opróżnia bufor i dopiero potem dostaje `Optional.empty()`.
4. `put()` po `close()` rzuca `IllegalStateException`, a wielokrotne `close()` jest idempotentne.
5. Producent czeka na pełnym buforze, `close()` go budzi $\to$ `IllegalStateException`.
6. Producent zablokowany na `wait()` zostaje przerwany $\to$ kończy z odtworzonym znacznikiem przerwania.
7. Konsument zablokowany na `wait()` zostaje przerwany analogicznie.
8. Bezpieczne sprzątanie i kontrola limitu czasu (`join(timeout)` i `isAlive()`).

---

### Zadanie 5: Eksperyment kontrolowany
Plik: `src/PerformanceExperiment.java`
- Pomiary czasu z `System.nanoTime()`.
- Zliczanie wywołań `wait()` producentów i konsumentów bezpośrednio pod monitorem przed wywołaniem `wait()`.
- Obliczanie mediany z 5 powtórzeń dla pojemności 1, 2, 5, 10, 50.

---

## 4. Pytania kontrolne z odpowiedziami

1. **Którą blokadę zwalnia wait() i kiedy wątek odzyskuje tę blokadę?**
   Wywołanie `wait()` atomowo zwalnia blokadę monitora obiektu, na którym zostało wywołane (i tylko tę blokadę; jeśli wątek trzymał inne blokady, nie zostaną one zwolnione). Wątek odzyskuje blokadę dopiero po obudzeniu (przez powiadomienie, przerwanie lub fałszywe wybudzenie) w momencie wyjścia z metody `wait()`, konkurując o nią na równych prawach z innymi wątkami.

2. **Dlaczego przebudzenie nie oznacza, że oczekiwany warunek jest prawdziwy?**
   Z trzech powodów:
   - *Spurious wakeups* (fałszywe przebudzenia bez powiadomienia, dozwolone przez specyfikację JVM).
   - Inny wątek mógł wyprzedzić obudzony wątek, wejść do monitora i zmienić stan zanim obudzony wątek ponownie zdobył blokadę.
   - Użycie `notifyAll()` budzi wszystkie wątki ze wspólnego `wait-set`, w tym takie, dla których predykat jest nadal fałszywy (np. obudzenie producenta, gdy bufor nadal jest pełny).

3. **Ile wait-set ma jeden obiekt używany jako monitor wewnętrzny?**
   Dokładnie jeden zbiór oczekujących (*wait-set*).

4. **Dlaczego w buforze z dwoma predykatami preferujemy notifyAll()?**
   Ponieważ obiekt ma tylko jeden *wait-set*, `notify()` budzi jeden losowy/arbitralny wątek. Jeśli konsument zwolni miejsce i wywoła `notify()`, JVM może obudzić innego konsumenta zamiast producenta. Obudzony konsument zobaczy, że bufor jest pusty, i zaśnie z powrotem. W ten sposób sygnał o wolnym miejscu zostaje "zgubiony", co może doprowadzić do trwałego zakleszczenia (*deadlock/livelock*). `notifyAll()` gwarantuje, że właściwy wątek otrzyma szansę sprawdzenia swojego warunku.

5. **Co stanie się z producentem czekającym na pełnym buforze, gdy inny wątek wywoła close()?**
   `close()` ustawia flagę `closed = true` i wykonuje `notifyAll()`. Oczekujący producent zostaje obudzony, odzyskuje monitor, pętla `while (size == capacity && !closed)` zostaje przerwana, po czym trafia na sprawdzenie `if (closed)` i rzuca `IllegalStateException`.

6. **Dlaczego konsument nie powinien kończyć natychmiast po samym ustawieniu flagi closed?**
   Ponieważ w buforze mogą znajdować się elementy wstawione przez producentów tuż przed zamknięciem. Konsument musi najpierw odebrać wszystkie zaległe elementy z bufora, a zakończyć pracę dopiero, gdy bufor jest ZARÓWNO zamknięty, JAK I pusty (`size == 0 && closed`). W przeciwnym razie dane zostałyby utracone.

7. **Czym różni się wait() od sleep() w odniesieniu do posiadanej blokady?**
   `wait()` atomowo zwalnia monitor obiektu, pozwalając innym wątkom na wejście do sekcji krytycznej i zmianę stanu. `Thread.sleep()` NIE zwalnia żadnych blokad trzymanych przez dany wątek; wątek śpi, blokując dostęp do monitora wszystkim innym wątkom.

8. **Jak należy potraktować InterruptedException w metodzie bufora, a jak na granicy zadania wykonywanego przez wątek?**
   - **W metodzie bufora (`put`, `take`):** należy zadeklarować `throws InterruptedException` i przekazać wyjątek w górę do wywołującego, ponieważ warstwa infrastruktury nie zna polityki anulowania aplikacji.
   - **Na granicy zadania wątku (`run()`):** należy przechwycić `InterruptedException`, przywrócić znacznik przerwania za pomocą `Thread.currentThread().interrupt()`, ewentualnie zwolnić zasoby lokalne w bloku `finally` i czysto zakończyć działanie metody `run()`.
