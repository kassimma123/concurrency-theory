final class Countdown implements Runnable {
    private final int from;

    Countdown(int from) {
        this.from = from;
    }

    @Override
    public void run() {
        for (int i = from; i >= 0; --i) {
            System.out.printf("%s: %d%n",
                Thread.currentThread().getName(), i);
            try{
                Thread.sleep(5);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }
        }
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        //trzy obiekty zadań Runnable z różnymi wart początkowymi
        Countdown task1 = new Countdown(11);
        Countdown task2 = new Countdown(9);
        Countdown task3 = new Countdown(5);

        //trzy obiekty thread o różnych nazwach i przypisujemy im zadania
        Thread thread1 = new Thread(task1, "watek-5");
        Thread thread2 = new Thread(task2, "watek-7");
        Thread thread3 = new Thread(task3, "watek-8");

        //spr stanu NEW przed start()
        System.out.println("Stan przed start()");
        System.out.println(thread1.getName() + ": " + thread1.getState());
        System.out.println(thread2.getName() + ": " + thread2.getState());
        System.out.println(thread3.getName() + ": " + thread3.getState());

        //uruchamianie każdego wątku metoda start()
        thread1.start();
        thread2.start();
        thread3.start();

        //próbkowanie stanów po starcie
        System.out.println("Próbki stanów w trakcie działania: ");
        for (int i=0; i<3; i++){
            System.out.printf("Próbka %d --> %s, %s: %s, %s: %s%n", i+1,
                    thread1.getName(), thread1.getState(),
                    thread2.getName(), thread2.getState(),
                    thread3.getName(), thread3.getState());
        }

        //czekamy na zakończenie każdego wątku
        thread1.join();
        thread2.join();
        thread3.join();

        System.out.println("main: wszystkie wątki zakończyły pracę");

        System.out.println("Stan po join(): ");
        System.out.println(thread1.getName() + ": " + thread1.getState());
        System.out.println(thread2.getName() + ": " + thread2.getState());
        System.out.println(thread3.getName() + ": " + thread3.getState());

    }
}
