package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;

import java.util.ArrayList;
import java.util.List;

public class Util {
    public static List<JClass> superClassesIncluded(JClass klass) {
        List<JClass> result = new ArrayList<>();

        JClass superKlass = klass;
        while (superKlass != null) {
            result.add(superKlass);
            superKlass = superKlass.getSuperClass();
        }
        return result;
    }

    public static List<JClass> subClassesIncluded(JClass klass, ClassHierarchy hierarchy) {
        return hierarchy.getAllSubclassesOf(klass).stream().toList();
    }
}
