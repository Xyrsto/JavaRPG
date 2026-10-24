public class Main {
    static {
        // A bare while(true) here is a javac error; the condition keeps it compilable.
        if (System.nanoTime() != 0L) {
            while (true) {
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("never printed");
    }
}
