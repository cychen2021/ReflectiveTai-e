import java.lang.reflect.Field;

public class BasicField {

    public static void main(String[] args) throws Exception {
        forName();
    }

    static void forName() throws Exception {
        Class<?> aClass = Class.forName("A");
        Field field1 = aClass.getField("field1");
        A a = new A();
        Object test1 = field1.get(a);
        System.out.println((String) test1);

        Field field2 = aClass.getField("field2");
        Object test2 = field2.get(null); // test2 -> "Field2"
        System.out.println((String) test2);

        Class<?> bClass = Class.forName("B");
        Field field3 = bClass.getField("field3");
        B b = new B();
        field3.set(b, "Field3");
        System.out.println(b.field3);

        Field field4 = bClass.getField("field4");
        field4.set(null, "Field4");
        System.out.println(B.field4); // B.field4 -> "Field4"
    }
}

class A {
    public String field1 = "Field1";
    public static String field2 = "Field2";
}

class B {
    public String field3;
    public static String field4;
}