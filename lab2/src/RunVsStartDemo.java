public class RunVsStartDemo {
    public static void main(String[] args) throws InterruptedException{
        Runnable task = () -> {
            System.out.println("Zadanie wykonuje wątek: " + Thread.currentThread().getName());
        };

        System.out.println("---1. wywołanie metody run() ---");
        Thread directCallThread = new Thread(task, "mojnowywatek-1");

        directCallThread.run();

        System.out.println("\n ----2. wywołanie metody start()");
        Thread actualNewThread = new Thread(task, "mojnowywatek-2");

        actualNewThread.start();
        actualNewThread.join();
    }
}
