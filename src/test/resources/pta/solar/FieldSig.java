import java.lang.reflect.Field;

public class FieldSig {
    public static void main(String[] args) throws Exception {
        invoke(unknown("field1"));
    }

    public static void invoke(String name) throws Exception {
        A a = new A();
        Class<?> c = A.class;
        Field field11 = c.getField(name);
        Y num = (Y) field11.get(a); // field11 -> f^A_u, f^A_{Object | X | Y | Z, u}
        System.out.println(num);

        Field field12 = c.getField(name);
        field12.set(a, new Y()); // field12 -> f^A_u, f^A_{Object | X | Y, u}
    }

    public static String unknown(String s) {
        return new String(s);
    }
}

class A {
    public Y field1 = new Y();
}

class X {
}

class Y extends X {

}

class Z extends Y {

}
