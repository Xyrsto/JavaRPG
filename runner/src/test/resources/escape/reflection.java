public class Main {
    public static void main(String[] args) throws Exception {
        Class<?> runtime = Class.forName("java.lang.Runtime");
        System.out.println(runtime.getName());
    }
}
