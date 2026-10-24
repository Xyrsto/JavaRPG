import java.net.Socket;

public class Main {
    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket("example.com", 80)) {
            System.out.println("connected");
        }
    }
}
