public class Counter {
    private int value;
    public synchronized void increment() {
        value++; // lock: this
    }
    public synchronized int get() {
        return value; // lock: this
    }
}