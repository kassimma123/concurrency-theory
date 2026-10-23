public class Worker implements Runnable {
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                doOneStep();
                Thread.sleep(20);
            }
        } catch (InterruptedException ex) {
            // sleep cleared the flag; restore it for callers and cleanup code
            Thread.currentThread().interrupt();
        } finally {
            releaseLocalResources();
        }
    }

    private void doOneStep() {
        // one finite unit of work
        System.out.println(Thread.currentThread().getName() + " wykonuje krok pracy");
    }

    private void releaseLocalResources() {
        // cleanup
        System.out.println(Thread.currentThread().getName() + " zwalnia zasoby");
    }
}
