public class Main {
    public static void main(String[] args) {
        String line = "x".repeat(1000);
        for (int i = 0; i < 100000; i++) {
            System.out.println(line);
        }
    }
}
