import java.lang.reflect.Field;

public class FieldS2T {
    public static void main(String[] args) throws Exception {
        invoke(unknown("B"));
    }

    public static void invoke(String name) throws Exception {
        Class<?> c = Class.forName(name);
        Object a = c.newInstance();
        Field field11 = c.getField("field1");
        Y num = (Y) field11.get(a); // field11 -> f^u_{u, field1}, f^(A | B)_{u, field1}
        System.out.println(num);

        Field field12 = c.getField("field1");
        field12.set(a, new Y()); // field12 -> f^u_{u, field1} f^(A | B)_{u, field1}
    }

    public static String unknown(String s) {
        return new String(s);
    }
}

class A {
    public Z field1 = new Z();
}

class B extends A {
    public Y field1 = new Y();
}

class X {
}

class Y extends X {

}

class Z extends Y {

}
