public class Main {
    static class Loader extends ClassLoader {
    }

    public static void main(String[] args) {
        System.out.println(new Loader().getClass().getName());
    }
}
