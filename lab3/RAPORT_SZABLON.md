# Raport: Teoria Współbieżności – Ćwiczenie 3
**Temat:** Monitory i oczekiwanie na warunek w Javie  
**Autor:** [Imię i Nazwisko]  
**Data:** [Data wykonania]  

---

## 1. Środowisko wykonawcze i kompilator
- **Wersja JDK:** OpenJDK 25.0.2 LTS (Temurin-25.0.2+10, build 25.0.2+10-LTS)
- **Kompilator:** javac 25.0.2 (`--release 25`)
- **System operacyjny:** macOS (Apple Silicon, 8 rdzeni logicznych)

---

## 2. Uzasadnienie niezmienników, pętli while, wyboru notifyAll() i protokołu zakończenia

### 2.1. Niezmienniki stanu bufora
Stan bufora pierścieniowego opisują zmienne: `capacity` (finalna pojemność), tablica `buffer`, indeksy `head` i `tail`, licznik `size` oraz flaga `closed`.
Przez cały czas życia bufora zachowane są niezmienniki:
1. $0 \le \text{size} \le \text{capacity}$ – bufor nigdy nie przechowuje ujemnej liczby elementów ani nie przekracza pojemności.
2. $0 \le \text{head}, \text{tail} < \text{capacity}$ – wskaźniki mieszczą się w granicach tablicy.
3. $\text{tail} = (\text{head} + \text{size}) \pmod{\text{capacity}}$ – określa położenie kolejnego wolnego miejsca względem początku logicznej kolejki FIFO.
4. Dokładnie `size` kolejnych pozycji poczynając od `head` przechowuje elementy nie-null, a pozostałe pozycje są wyzerowane (`null`). Zapobiega to wyciekom pamięci (*memory leak*) i pozwala Garbage Collectorowi zwalniać pamięć.
5. Flaga `closed` jest monotoniczna (może zmienić wartość wyłącznie z `false` na `true`).

Wszystkie operacje modyfikujące lub odczytujące ten stan są wykonywane pod pojedynczą blokadą monitora instancji (`synchronized`), co ustanawia relację *happens-before* i gwarantuje spójność pamięciową.

### 2.2. Użycie pętli `while` zamiast `if`
Użycie `if` zamiast `while` przy oczekiwaniu na warunek jest krytycznym błędem współbieżności z następujących powodów:
- **Fałszywe wybudzenia (*spurious wakeups*):** specyfikacja JVM zezwala na wybudzenie wątku z metody `wait()` bez wywołania `notify()` lub `notifyAll()`.
- **Wyścig o odzyskanie monitora (*race condition*):** po powiadomieniu `notifyAll()` kilka wątków zostaje przeniesionych ze zbioru *wait-set* do kolejki wejściowej monitora. Zanim dany wątek odzyska blokadę, inny obudzony wątek może wejść do sekcji krytycznej jako pierwszy i zmienić stan (np. inny producent zapełni bufor). Wątek sprawdzający warunek przez `if` kontynuowałby wykonanie na fałszywym predykacie, co doprowadziłoby do nadpisania danych w pełnym buforze lub pobrania `null` z pustego bufora.
- Pętla `while` wymusza ponowne sprawdzenie predykatu po KAŻDYM odzyskaniu monitora.

### 2.3. Wybór `notifyAll()` zamiast `notify()`
Monitor wewnętrzny w Javie posiada tylko **jeden wspólny zbiór oczekujących (*wait-set*)** dla wszystkich predykatów ("bufor niepełny", "bufor niepusty", "bufor zamknięty").
Metoda `notify()` budzi dokładnie jeden, arbitralnie wybrany wątek. Jeśli konsument zwolni miejsce w buforze i wywoła `notify()`, JVM może obudzić innego konsumenta zamiast czekającego producenta. Obudzony konsument stwierdzi, że bufor jest pusty i natychmiast zaśnie ponownie w `wait()`. W ten sposób sygnał o wolnym miejscu zostaje utracony, producenci nadal czekają na zwolnienie miejsca, a konsumenci na pojawienie się danych, co prowadzi do **trwałego zakleszczenia (*deadlock/starvation*)**.
`notifyAll()` budzi wszystkie oczekujące wątki, gwarantując, że każdy zainteresowany wątek zbadana swój predykat.

### 2.4. Protokół kontrolowanego zakończenia przez `close()`
Zastosowano protokół idempotencyjnego zamykania bufora:
- Wątek koordynujący po dołączeniu wszystkich producentów (`join()`) wywołuje `buffer.close()`.
- Metoda `close()` ustawia monotoniczną flagę `closed = true` i budzi wszystkich oczekujących za pomocą `notifyAll()`.
- Producenci próbujący zapisać do zamkniętego bufora lub obudzeni na pełnym buforze otrzymują wyjątek `IllegalStateException`.
- Konsumenci kontynuują pobieranie danych aż do momentu, gdy bufor zostanie całkowicie opróżniony. Dopiero gdy `size == 0 && closed`, metoda `take()` zwraca `Optional.empty()`.
- Protokół nie wymaga zgadywania liczby operacji konsumentów ani stosowania sztucznych znaczników końca per-konsument (*poison pills*).

---

## 3. Polityka obsługi `InterruptedException`
W projekcie przyjęto spójną politykę dwuwarstwową:
1. **Wewnątrz metod monitora (`put`, `take`):** wyjątek `InterruptedException` NIE jest tłumiony. Metody deklarują `throws InterruptedException` i propagują go bezpośrednio do wywołującego. Warstwa bufora nie zna intencji aplikacji ani nie wie, czy przerwanie oznacza żądanie anulowania zadania, czy shutdown całej aplikacji.
2. **Na granicy zadania wątku (metoda `run()` w klasach `Runnable` / `Thread`):** wyjątek jest przechwytywany w bloku `catch (InterruptedException e)`. Zgodnie z dobrymi praktykami Javy, rzucenie `InterruptedException` czyści znacznik przerwania wątku, dlatego w obsłudze wyjątku natychmiast odtwarzamy ten stan:
   ```java
   Thread.currentThread().interrupt();
   ```
   a następnie kończymy wykonanie metody `run()`.

---

## 4. Wyniki testów stanów granicznych i przerwań (Zadanie 4)

Wszystkie 8 deterministycznych testów zaimplementowanych w `BoundedBufferTest` zakończyło się powodzeniem:

| Nr | Test | Rezultat |
|:--:|:-------------------------------------------------------------------|:--------:|
| 1  | Sekwencyjny FIFO ($C=2$) z zawinięciem indeksów head/tail          | PASSED   |
| 2  | Konsument czeka na pustym buforze, `close()` go budzi $\to$ empty  | PASSED   |
| 3  | Po `close()` konsument drenuje bufor, a na końcu otrzymuje empty   | PASSED   |
| 4  | `put()` po `close()` rzuca `IllegalStateException`, idempotentność | PASSED   |
| 5  | Producent czeka na pełnym buforze, `close()` budzi $\to$ wyjątek   | PASSED   |
| 6  | Producent na `wait()` przerwany $\to$ odtwarza znacznik przerwania | PASSED   |
| 7  | Konsument na `wait()` przerwany $\to$ odtwarza znacznik przerwania | PASSED   |
| 8  | Limit czasu i bezpieczne sprzątanie wątków (`join(timeout)`)       | PASSED   |

---

## 5. Wyniki eksperymentu kontrolowanego (Zadanie 5)

Eksperyment przeprowadzono dla stałego obciążenia 20 000 elementów na każdego producenta. Wyniki stanowią medianę z 5 niezależnych powtórzeń:

| Pojemność ($C$) | Producenci ($P$) | Konsumenci ($K$) | Mediana Czasu [ms] | Mediana wait() Prod | Mediana wait() Kons |
|:---------------:|:----------------:|:----------------:|:------------------:|:-------------------:|:-------------------:|
| 1               | 1                | 1                | 132                | 19 982              | 19 987              |
| 1               | 4                | 1                | 1074               | 288 757             | 79 933              |
| 1               | 1                | 4                | 290                | 19 962              | 79 890              |
| 1               | 4                | 4                | 910                | 141 653             | 159 203             |
| 2               | 1                | 1                | 67                 | 9 991               | 10 004              |
| 2               | 4                | 1                | 514                | 139 269             | 39 984              |
| 2               | 1                | 4                | 148                | 9 991               | 39 985              |
| 2               | 4                | 4                | 520                | 79 999              | 98 150              |
| 5               | 1                | 1                | 27                 | 3 994               | 4 006               |
| 5               | 4                | 1                | 224                | 56 103              | 15 999              |
| 5               | 1                | 4                | 60                 | 3 993               | 15 989              |
| 5               | 4                | 4                | 229                | 36 134              | 40 706              |
| 10              | 1                | 1                | 14                 | 1 994               | 2 006               |
| 10              | 4                | 1                | 101                | 25 500              | 8 003               |
| 10              | 1                | 4                | 29                 | 1 991               | 7 986               |
| 10              | 4                | 4                | 130                | 21 185              | 23 021              |
| 50              | 1                | 1                | 4                  | 353                 | 445                 |
| 50              | 4                | 1                | 31                 | 5 660               | 1 426               |
| 50              | 1                | 4                | 7                  | 242                 | 1 268               |
| 50              | 4                | 4                | 43                 | 5 361               | 6 220               |

---

## 6. Odpowiedzi na pytania analityczne z Zadania 5

### 6.1. Kiedy ograniczeniem są producenci, kiedy konsumenci, a kiedy pojemność?
- **Ograniczenie pojemnością ($C$ małe, np. 1 lub 2):** bufor stanowi wąskie gardło (*bottleneck*). Liczba wywołań `wait()` jest zbliżona do liczby przesyłanych elementów. Wątki spędzają większość czasu na przełączaniu kontekstu i synchronizacji.
- **Ograniczenie konsumentami ($P > K$, np. 4P / 1K):** producenci produkują szybciej niż pojedynczy konsument jest w stanie odebrać. Widać to w drastycznym wzroście liczby oczekiwań producentów (`Mediana wait() Prod` wynosiła aż 288 757 dla $C=1$ oraz 25 500 dla $C=10$).
- **Ograniczenie producentami ($P < K$, np. 1P / 4K):** konsumenci stale drenują bufor do zera i konkurują o rzadko pojawiające się elementy. Konsumenci notują wysoką liczbę `wait()`, a producenci rzadko czekają na wolne miejsce.

### 6.2. Dlaczego mniejsza liczba wywołań wait() nie musi oznaczać proporcjonalnie krótszego czasu?
1. **Koszt rywalizacji o blokadę monitora (*lock contention*):** wątek, który nie wykonuje `wait()`, nadal musi wejść do sekcji `synchronized`. Przy dużej liczbie wątków czas tracony jest w kolejce wejściowej monitora na poziomie jądra systemu operacyjnego (parkowanie wątków, context switch).
2. **Efekt "stada" (*thundering herd problem*):** wywołanie `notifyAll()` budzi jednocześnie wszystkie oczekujące wątki. Choć tylko jeden z nich może faktycznie zająć monitor i wykonać postęp, pozostałe wątki konkurują o blokadę, co generuje narzut CPU bez zmniejszenia liczby logicznych operacji.
3. **Pojemność bufora a amortyzacja:** przy większej pojemności wątki mogą wykonać kilka operacji z rzędu bez blokowania, co lepiej wykorzystuje pamięć podręczną CPU (*cache locality*).

### 6.3. Dlaczego zamiana while na if jest błędem nawet wtedy, gdy błąd nie ujawnia się podczas wielu uruchomień?
Zamiana `while` na `if` łamie formalną własność **bezpieczeństwa (*safety property*)**. W systemach współbieżnych brak ujawnienia się błędu w testach empirycznych nie dowodzi jego poprawności (testy sprawdzają tylko podzbiór możliwych przeplotów). Wystarczy jedno rzadkie fałszywe wybudzenie (*spurious wakeup*) lub przeplot, w którym inny obudzony wątek zdobędzie monitor ułamek nanosekundy wcześniej, aby wątek z `if` dokonał niepoprawnego zapisu/odczytu, powodując korupcję pamięci, zamazanie danych lub `NullPointerException`.

### 6.4. Jaki problem może spowodować zamiana notifyAll() na notify() przy jednym wspólnym wait-set?
Prowadzi do **zagłodzenia lub zakleszczenia (*deadlock/lost notification*)**. Ponieważ monitor Javy ma tylko jeden *wait-set*, `notify()` budzi dowolny, nieokreślony wątek. Jeżeli konsument zwalniający miejsce obudzi innego konsumenta zamiast producenta, obudzony konsument zobaczy pusty bufor i powróci do `wait()`. W ten sposób sygnał o dostępnym miejscu zanika, a żaden producent nie zostaje powiadomiony, co może całkowicie zatrzymać postęp całego systemu.

---

## 7. Odpowiedzi na pytania kontrolne (strona 8 konspektu)

1. **Którą blokadę zwalnia wait() i kiedy wątek odzyskuje tę blokadę?**  
   `wait()` zwalnia wyłącznie blokadę monitora obiektu, na którym zostało wywołane. Wątek odzyskuje tę blokadę dopiero po zakończeniu oczekiwania (wybudzeniu), konkurując o ponowne jej zdobycie z innymi wątkami przed powrotem z metody `wait()`.

2. **Dlaczego przebudzenie nie oznacza, że oczekiwany warunek jest prawdziwy?**  
   Przebudzenie jest jedynie sygnałem, że stan MÓGŁ ulec zmianie. Inny wątek mógł wyprzedzić wybudzony wątek i ponownie unieważnić warunek przed odzyskaniem monitora. Mogło także dojść do fałszywego przebudzenia (*spurious wakeup*) lub wybudzenia przez `notifyAll()` ze wspólnego *wait-set* dla wątku o innym predykacie.

3. **Ile wait-set ma jeden obiekt używany jako monitor wewnętrzny?**  
   Dokładnie jeden.

4. **Dlaczego w buforze z dwoma predykatami preferujemy notifyAll()?**  
   Wspólny *wait-set* oznacza, że `notify()` może wybudzić wątek czekający na drugi, niespełniony predykat. Może to doprowadzić do "zgubienia" powiadomienia i trwałego zakleszczenia. `notifyAll()` budzi wszystkie wątki, dając szansę wątkom o spełnionym predykacie.

5. **Co stanie się z producentem czekającym na pełnym buforze, gdy inny wątek wywoła close()?**  
   Zostanie obudzony przez wywołanie `notifyAll()` wewnątrz `close()`. Po odzyskaniu monitora zauważy zmianę flagi `closed == true` i zgłosi wyjątek `IllegalStateException`.

6. **Dlaczego konsument nie powinien kończyć natychmiast po samym ustawieniu flagi closed?**  
   Ponieważ w buforze mogą znajdować się elementy wstawione przez producentów przed zamknięciem. Konsument musi je najpierw odebrać, aby nie doszło do utraty danych. Kończy pracę dopiero, gdy bufor jest pusty i zamknięty.

7. **Czym różni się wait() od sleep() w odniesieniu do posiadanej blokady?**  
   `wait()` atomowo zwalnia posiadaną blokadę monitora i umieszcza wątek w zbiorze oczekujących. `sleep()` wstrzymuje wykonanie wątku, ale NIE zwalnia żadnej blokady.

8. **Jak należy potraktować InterruptedException w metodzie bufora, a jak na granicy zadania wykonywanego przez wątek?**  
   W metodzie bufora należy zadeklarować `throws InterruptedException` i przekazać wyjątek w górę. Na granicy zadania (`run()`) należy przechwycić wyjątek, odtworzyć znacznik przerwania (`Thread.currentThread().interrupt()`), zwolnić zasoby i zakończyć zadanie.
