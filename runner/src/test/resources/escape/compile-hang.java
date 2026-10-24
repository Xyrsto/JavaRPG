public class Solution {
    interface A { Object fa(Object x); }
    interface B { Object fb(Object x); }
    static void m(A a) { }
    static void m(B b) { }
    static void go() {
        m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> m(x -> null))))))))))))))))))))))))))))))))))))))));
    }
}
