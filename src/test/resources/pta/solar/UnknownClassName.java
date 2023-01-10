import java.lang.reflect.Method;

public class UnknownClassName {

    public static void main(String[] args) throws Exception {
        invokeOneArg(unknown("F"), new Class[]{Object.class}, new F());
        invokeOneArg(unknown("H"), new Class[]{Object.class}, new H());
        invokeOneArg(unknown("H"), new Class[]{String.class}, new H());
    }

    static void invokeOneArg(String name, Class<?>[] paramTypes, Object recv) throws Exception {
        Class<?> c = Class.forName(name);
        Method oneArg = c.getMethod("oneArg", paramTypes);
        String arg = "arg";
        oneArg.invoke(recv, arg); // <F: void oneArg(Object)>,
                                  // <G: void oneArg(Object)>,
                                  // <H: void oneArg(String)>
    }

    static String unknown(String s) {
        return new String(s);
    }
}

class F {

    public void nonArg() {
        System.out.println("F.nonArg()");
    }

    public void oneArg(Object o) {
        System.out.println("F.oneArg(Object)");
    }

    public void oneArg2(Object o) {
        System.out.println("F.oneArg2(Object)");
    }
}

class G {

    public void oneArg(Object o) {
        System.out.println("G.oneArg(Object)");
    }
}

class H extends G {

    public void oneArg(String s) {
        System.out.println("H.oneArg(String)");
    }

    public void twoArgs(Object o1, Object o2) {
        System.out.println("H.twoArgs(Object,Object)");
    }
}
