import java.lang.reflect.Field;

public class LazyHeapField {
    public static void main(String[] args) throws Exception {
       invoke(unknown("A"), unknown("A")); 
    }

    static String unknown(String s) {
        return new String(s);
    }


    static void invoke(String name1, String name2) throws Exception {
        Class<?> c1 = Class.forName(name1);
        Object obj1 = c1.newInstance();
        Field field1 = A.class.getField("Field1");
        Object str1 = field1.get(obj1); // obj1 -> o^u, o^A
        System.out.println(str1);

        Class<?> c2 = Class.forName(name2);
        Object obj2 = c2.newInstance();
        Field field2 = A.class.getField("Field2");
        field2.set(obj2, "Hello"); // obj2 -> o^u, o^A
    }
}

class A {
    public String field1 = "Field1";
    public String field2 = "Field2";
}
