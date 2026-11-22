import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CoarseGrainedList {

    private static class Node {
        int item;
        Node next;

        Node(int item) {
            this.item = item;
            this.next = null;
        }
    }

    private final Lock lock = new ReentrantLock();
    private final Node head;

    public CoarseGrainedList() {
        // tworzenie wartowników
        this.head = new Node(Integer.MIN_VALUE);
        this.head.next = new Node(Integer.MAX_VALUE);
    }

    //czy nalezy 
    public boolean contains(int item){
        lock.lock();
        try{
            Node curr = head.next;
            while (curr.item < item){
                curr = curr.next;
            }
            return curr.item == item;
        } finally {
            lock.unlock();
        }
    }

    //dodawanie
    public boolean add(int item){
        lock.lock();
        try{
            Node pred = head;
            Node curr = head.next;

            while (item > curr.item){
                pred = curr;
                curr = curr.next;
            }

            if (item == curr.item){
                return false;
            }else{
                Node newNode = new Node(item);
                pred.next = newNode;
                newNode.next = curr;
                return true;
            }
        }finally{
           lock.unlock(); 
        }

    }

    //usuwanie
    public boolean remove(int item){
        lock.lock();
        try{
            Node curr = head.next;
            Node pred = head;
            
            while (item > curr.item){
                pred = curr;
                curr = curr.next; 
            }

            if (item == curr.item){
                pred.next = curr.next;
                return true;
            }else{
                return false;
            }
        }finally{
            lock.unlock(); 
        }
    }

}
