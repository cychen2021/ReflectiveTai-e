package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.World;
import pascal.taie.ir.exp.ExpVisitor;
import pascal.taie.ir.exp.ReferenceLiteral;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.language.type.ReferenceType;
import pascal.taie.language.type.Type;
import static pascal.taie.language.classes.ClassNames.METHOD;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MethodMetaLiteral implements ReferenceLiteral {
    private Boolean isStatic;

    private final ExtendedJClass baseClass;

    private final MethodRef methodRef;

    private final Optional<String> methodName;

    private final ExtendedType returnType;

    private final Optional<List<ExtendedType>> parameterTypes;

    public boolean signatureUnknown() {
        if (!isUnknown()) {
            return false;
        }
        return methodNameUnknown() || returnTypeUnknown() || parameterTypesUnknown()
                || parameterTypesPartiallyKnown();
    }

    public boolean isUnknown() {
        return methodRef == null;
    }

    public boolean methodNameUnknown() {
        return isUnknown() && methodName.isEmpty();
    }

    public boolean parameterTypesUnknown() {
        return isUnknown() && parameterTypes.isEmpty();
    }

    public boolean returnTypeUnknown() {
        return isUnknown() && returnType.isUnknown();
    }

    public boolean parameterTypesPartiallyKnown() {
        if (!isUnknown() || parameterTypesUnknown()) {
            return false;
        }
        for (ExtendedType extTypes: parameterTypes.get()) {
            if (extTypes.isUnknown()) {
                return true;
            }
        }
        return false;
    }

    public boolean baseClassUnknown() {
        return isUnknown() && baseClass.isUnknown();
    }

    private MethodMetaLiteral(MethodRef methodRef) {
        this.methodRef = methodRef;
        this.methodName = null;
        this.returnType = null;
        this.parameterTypes = null;
        this.baseClass = null;
        this.isStatic = null;
    }

    private MethodMetaLiteral(ExtendedJClass baseClass, Optional<String> name,
                              Optional<List<ExtendedType>> parameterTypes, ExtendedType returnType,
                              boolean isStatic) {
        this.methodRef = null;
        this.methodName = name;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.baseClass = baseClass;
        this.isStatic = isStatic;
    }

    public boolean isStatic() {
        if (isUnknown()) {
            assert isStatic != null;
            return isStatic;
        }
        return methodRef.isStatic();
    }

    public MethodRef getMethodRef() {
        return isUnknown() ? null : methodRef;
    }

    @Override
    public <T> T accept(ExpVisitor<T> visitor) {
        return null;
    }

    @Override
    public ReferenceType getType() {
        return World.get().getTypeSystem().getClassType(METHOD);
    }

    public static MethodMetaLiteral from(ExtendedJClass baseClass, Optional<String> name,
                                         Optional<List<ExtendedType>> parameterTypes,
                                         ExtendedType returnType, boolean isStatic) {
        if (baseClass.isUnknown() || name.isEmpty() || parameterTypes.isEmpty()
                || returnType.isUnknown()) {
            return new MethodMetaLiteral(baseClass, name, parameterTypes, returnType, isStatic);
        }
        List<Type> actualParaTypes = new ArrayList<>();
        for (ExtendedType extType: parameterTypes.get()) {
            if (extType.isUnknown()) {
                return new MethodMetaLiteral(baseClass, name, parameterTypes, returnType, isStatic);
            }
            actualParaTypes.add(extType.getSome());
        }

        MethodRef methodRef = MethodRef.get(baseClass.getSome(), name.get(), actualParaTypes,
                                            returnType.getSome(), isStatic);
        return new MethodMetaLiteral(methodRef);
    }
}
