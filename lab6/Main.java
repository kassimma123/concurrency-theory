public class Main {
    public static void main(String[] args) {
        FineGrainedList list = new FineGrainedList();

        System.out.println("Dodaj 10: " + list.add(10));      // true
        System.out.println("Dodaj 20: " + list.add(20));      // true
        System.out.println("Dodaj znowu 10: " + list.add(10)); // false (już jest!)

        System.out.println("Czy ma 10: " + list.contains(10)); // true
        System.out.println("Czy ma 15: " + list.contains(15)); // false

        System.out.println("Usuń 10: " + list.remove(10));    // true
        System.out.println("Czy ma 10: " + list.contains(10)); // false
        System.out.println("Usuń znowu 10: " + list.remove(10)); // false
    }
}