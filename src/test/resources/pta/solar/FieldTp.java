import java.lang.reflect.Field;

public class FieldTp {
    public static void main(String[] args) throws Exception {
        invoke(unknown("A"));
    }

    public static void invoke(String name) throws Exception {
        A a = new A();
        Class<?> c = Class.forName(name);
        Field field1 = c.getField("field1");
        Object str = field1.get(a); // field1 -> f^u_*, f^A_*
        System.out.println(str);

        Field field2 = c.getField("field2");
        field2.set(a, "Hello"); // field2 -> f^u_*, f^A_*
    }

    public static String unknown(String s) {
        return new String(s);
    }
}

class A {
    public String field1 = "Field1";
    public String field2 = "Field2";
}
