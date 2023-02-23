import java.lang.reflect.Method;

public class RecvType {

    public static void main(String[] args) throws Exception {
        invoke(unknown("K"), "foo");
        invoke(unknown("K"), "bar");
    }

    static void invoke(String cName, String mName) throws Exception {
        Class<?> c = Class.forName(cName);
        Method m = c.getMethod(mName);
        K k = new K();
        m.invoke(k); // <K: void foo()>, <K: void bar()>
    }

    static String unknown(String s) {
        return new String(s);
    }
}

class K {

    public void foo() {
        System.out.println("K.foo()");
    }

    public void bar() {
        System.out.println("K.bar()");
    }

    public void baz() {
        System.out.println("K.baz()");
    }
}
