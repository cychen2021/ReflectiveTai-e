import java.lang.reflect.Method;

public class Inheritance {

    public static void main(String[] args) throws Exception {
        invokeFoo();
        invokeBar();
        invokeBar2();
    }

    static void invokeFoo() throws Exception {
        Method foo = Derived.class.getMethod("foo");
        Derived o = new Derived();
        foo.invoke(o); // <Base: void foo()>
    }

    static void invokeBar() throws Exception {
        Method bar = Derived.class.getMethod("bar");
        Derived o = new Derived();
        bar.invoke(o); // <Derived: void bar()>
    }

    static void invokeBar2() throws Exception {
        Method bar = Base.class.getMethod("bar");
        Derived o = new Derived();
        bar.invoke(o); // <Derived: void bar()>
    }
}

class Base {

    public void foo() {
        System.out.println("Base.foo()");
    }

    public void bar() {
        System.out.println("Base.bar()");
    }
}

class Middle extends Base {}

class Derived extends Middle {

    @Override
    public void bar() {
        System.out.println("Derived.bar()");
    }
}
