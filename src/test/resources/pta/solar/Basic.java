import java.lang.reflect.Method;

public class Basic {

    public static void main(String[] args) throws Exception {
        dotClass();
        forName();
    }

    static void dotClass() throws Exception {
        Method print1 = A.class.getMethod("print1");
        A a = new A();
        print1.invoke(a); // <A: void print1()>
    }

    static void forName() throws Exception {
        Class<?> aClass = Class.forName("A");
        Method print2 = aClass.getMethod("print2");
        A a = new A();
        print2.invoke(a); // <A: void print2()>
    }
}

class A {

    public void print1() {
        System.out.println("A.print1()");
    }

    public void print2() {
        System.out.println("A.print2()");
    }
}
