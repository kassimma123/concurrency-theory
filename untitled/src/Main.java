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
        }
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Countdown countdown = new Countdown(5);
        Thread worker = new Thread(countdown, "worker-1");
        worker.start(); // <-- countdown.run();
        worker.join();
        System.out.println("main: worker zakonczyl prace");
    }
}
