package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.language.classes.JClass;

import java.util.ArrayList;
import java.util.List;

public class Util {
    public static List<JClass> superClassesOf(JClass klass) {
        List<JClass> result = new ArrayList<>();

        JClass superKlass = klass.getSuperClass();
        while (superKlass != null) {
            result.add(superKlass);
            superKlass = superKlass.getSuperClass();
        }
        return result;
    }
}
