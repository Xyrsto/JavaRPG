import java.io.FileWriter;

public class Main {
    public static void main(String[] args) throws Exception {
        try (FileWriter writer = new FileWriter("/escape-canary.txt")) {
            writer.write("escaped");
        }
        System.out.println("written");
    }
}
