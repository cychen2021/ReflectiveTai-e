import java.lang.reflect.Method;

public class CastRecv {

    public static void main(String[] args) throws Exception {
        invokeOneArg(unknown("G"), new Class[]{Object.class}, Class.forName(unknown("H")).newInstance());
        invokeOneArg(unknown("G"), new Class[]{String.class}, Class.forName(unknown("H")).newInstance());
    }

    static void invokeOneArg(String name, Class<?>[] paramTypes, Object recv) throws Exception {
        Class<?> c = Class.forName(name);
        Method oneArg = c.getMethod("oneArg", paramTypes);
        String arg = "arg";
        G g = (G) recv;
        oneArg.invoke(g, arg);    // <G: void oneArg(Object)>
    }

    static String unknown(String s) {
        return new String(s);
    }
}

class F {

    public void nonArg() {
        System.out.println("F.nonArg()");
    }

    public void oneArg(String o) {
        System.out.println("F.oneArg(String)");
    }
    
    public void oneArg(Integer o) {
        System.out.println("F.oneArg(Integer)");
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
