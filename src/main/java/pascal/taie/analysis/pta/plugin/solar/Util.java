package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.NewArray;
import pascal.taie.ir.stmt.New;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

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

    static boolean isLazyObjUnknownType(CSObj obj) {
         if (obj.getObject().getAllocation() instanceof LazyObj lazy) {
             return lazy.equals(LazyObj.TYPE_UNKNOWN);
         }
         return false;
    }
    static boolean paramTypesFit(TypeSystem typeSystem, List<Type> mtdParamTypes, List<Type> givenParamTypes) {
        if (mtdParamTypes.size() != givenParamTypes.size()) {
            return false;
        }
        for (int i = 0; i < mtdParamTypes.size(); i++) {
            if (!typeSystem.isSubtype(mtdParamTypes.get(i), givenParamTypes.get(i))) {
                return false;
            }
        }
        return true;
    }
}
