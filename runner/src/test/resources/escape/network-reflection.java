public class Main {
    public static void main(String[] args) throws Exception {
        var k = Main.class;
        Class<?> socket = k.forName("java.net.Socket");
        Object s = socket.getConstructor(String.class, int.class).newInstance("10.0.0.1", 80);
        System.out.println("connected " + s);
    }
}
