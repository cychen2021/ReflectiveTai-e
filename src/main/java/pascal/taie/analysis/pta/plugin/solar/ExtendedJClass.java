package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.World;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.ir.exp.ClassLiteral;
import pascal.taie.language.classes.ClassNames;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.type.ArrayType;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;

public class ExtendedJClass {
    private JClass jClass;

    public JClass getJClass() {
        return isUnknown() ? null : jClass;
    }

    public boolean isUnknown() {
        return jClass == null;
    }

    private ExtendedJClass(JClass jClass) {
        this.jClass = jClass;
    }

    private ExtendedJClass() {
        this.jClass = null;
    }

    public static ExtendedJClass get(JClass jClass) {
        return new ExtendedJClass(jClass);
    }

    private static final ExtendedJClass unknown = new ExtendedJClass();

    public static ExtendedJClass unknown() {
        return unknown;
    }

    public static ExtendedJClass from(CSObj csObj) {
        Object alloc = csObj.getObject().getAllocation();
        if (alloc instanceof ExtendedClassLiteral klass) {
            if (klass.isUnknown()) {
                return ExtendedJClass.unknown();
            }
            Type type = klass.getClassLiteral().getTypeValue();
            if (type instanceof ClassType classType) {
                return ExtendedJClass.get(classType.getJClass());
            } else if (type instanceof ArrayType) {
                return ExtendedJClass.get(World.get().getClassHierarchy()
                        .getJREClass(ClassNames.OBJECT));
            }
        }
        return null;
    }
}
