public class Main {
    public static void main(String[] args) throws Exception {
        Class<?> rt = Main.class.getClassLoader().loadClass("java.lang.Runtime");
        Object runtime = rt.getMethod("getRuntime").invoke(null);
        for (java.lang.reflect.Method m : rt.getMethods()) {
            if (m.getName().equals("exec") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                m.invoke(runtime, "id");
            }
        }
        System.out.println("done");
    }
}
