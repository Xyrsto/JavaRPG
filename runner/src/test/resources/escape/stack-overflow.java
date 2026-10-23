public class Main {
    static int deeper(int n) {
        return deeper(n + 1) + 1;
    }

    public static void main(String[] args) {
        System.out.println(deeper(0));
    }
}
