import java.lang.reflect.Method;

public class MethodInference {

    public static void main(String[] args) throws Exception {
        invokeOneArg("G", new Class[]{Object.class}, Class.forName(unknown("G")).newInstance());
    }

    static void invokeOneArg(String name, Class<?>[] paramTypes, Object recv) throws Exception {
        Class<?> c = Class.forName(name);
        Method oneArg = c.getMethod("oneArg", paramTypes);
        String arg = "arg";
        oneArg.invoke(recv, arg); // <G: void oneArg(Object)>,
                                  // <H: void oneArg(Object)>,
                                  // recv -> o^u, o^G, o^H
                                  // %solar-transformation-fresh-arg-2 -> "arg"

        Class<?> eC = Class.forName("E");
        Method oneArgE = eC.getMethod("oneArg", new Class[]{H.class});
        oneArgE.invoke(new E(), recv); // <E: void oneArg(H)>,
                                       // %solar-transfomation-fresh-arg-0-> o^H
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

    public void oneArg(Object o) {
        System.out.println("H.oneArg(Object)");
    }

    public void twoArgs(Object o1, Object o2) {
        System.out.println("H.twoArgs(Object,Object)");
    }
}

class E {
    public void oneArg(H h) {
        System.out.println("E.oneArg(H)");
    }
    
    public void oneArg(F f) {
        System.out.println("E.oneArg(F)");
    }
}
