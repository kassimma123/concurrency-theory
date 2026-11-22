import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FineGrainedList {

    private static class Node {
        int item;
        Node next;
        final Lock lock = new ReentrantLock();

        Node(int item) {
            this.item = item;
            this.next = null;
        }
        void lock(){lock.lock();}
        void unlock(){lock.unlock();}
    }

    private final Node head;

    public FineGrainedList() {
        // tworzenie wartowników
        this.head = new Node(Integer.MIN_VALUE);
        this.head.next = new Node(Integer.MAX_VALUE);
    }

    // czy nalezy
    public boolean contains(int item) {
        Node curr = head;

        try {
            
            curr.lock();
            while (curr.item < item) {
                curr.next.lock();
                curr.unlock();
                curr = curr.next;
            }

            return curr.item == item;
        } finally {
            curr.unlock();
        }
    }

    // dodawanie
    public boolean add(int item) {
        head.lock();
        Node pred = head;

        try {

            Node curr = pred.next;
            curr.lock();

            try{
                
                while (item > curr.item) {

                pred.unlock();
                pred = curr;
                curr = curr.next;
                curr.lock();
                }

                if (item == curr.item) {
                    return false;
                } else {

                    Node newNode = new Node(item);
                    pred.next = newNode;
                    newNode.next = curr;
                    return true;

                }
            } finally {
                curr.unlock();
            }
        }finally{
            pred.unlock();
        }
}

    // usuwanie
    public boolean remove(int item) {
        head.lock();
        Node pred = head;

        try {

            Node curr = pred.next;
            curr.lock();

            try{
                
                while (item > curr.item) {

                pred.unlock();
                pred = curr;
                curr = curr.next;
                curr.lock();
                }

                if (item == curr.item) {
                    pred.next = curr.next;
                    return true;
                } else {
                    return false;
                }
            } finally {
                curr.unlock();
            }
        }finally{
            pred.unlock();
        }  
    }

}
