package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.NewArray;
import pascal.taie.ir.stmt.New;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;

import java.util.ArrayList;
import java.util.List;

class Util {
     static int constArraySize(Obj obj) {
        var alloc = obj.getAllocation();
        if (!(alloc instanceof New newVar)) {
            return -1;
        }
        if (!(newVar.getRValue() instanceof NewArray arr)) {
            return -1;
        }
        var arrSizeVar = arr.getLength();
        if (!arrSizeVar.isConst()) {
            return -1;
        }
        var arrSizeConst = arrSizeVar.getConstValue();
        if (!(arrSizeConst instanceof IntLiteral intLiteral)) {
            return -1;
        }
        return intLiteral.getValue();
    }

    static List<JClass> superClassesOf(JClass jClass) {
        List<JClass> superClasses = new ArrayList<>();
        jClass = jClass.getSuperClass();
        while (jClass != null) {
            superClasses.add(jClass);
            jClass = jClass.getSuperClass();
        }
        return superClasses;
    }

    static List<JClass> superClassesOfIncluded(JClass jClass) {
        List<JClass> superClasses = superClassesOf(jClass);
        superClasses.add(0, jClass);
        return superClasses;
    }

    static boolean typeIsUnknown(Type type) {
         if (type instanceof ClassType classType) {
             return classType.getName().equals("UnknownClassName");
         }
         return false;
    }
}
