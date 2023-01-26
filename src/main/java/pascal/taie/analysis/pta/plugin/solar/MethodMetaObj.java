package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.World;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;

import javax.annotation.Nullable;

import static pascal.taie.language.classes.ClassNames.METHOD;

import java.util.*;

public class MethodMetaObj {
    record SignatureRecord(String methodName, List<Type> paramTypes, Type returnType) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SignatureRecord signature = (SignatureRecord) o;
            return Objects.equals(methodName, signature.methodName) && Objects.equals(paramTypes, signature.paramTypes) && Objects.equals(returnType, signature.returnType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodName, paramTypes, returnType);
        }

        public static SignatureRecord of(@Nullable String methodName, @Nullable List<Type> paramTypes, @Nullable Type returnType) {
            return new SignatureRecord(methodName, paramTypes, returnType);
        }
    }

    private final JClass baseClass;

    public JClass getBaseClass() {
        return baseClass;
    }

    private final SignatureRecord signature;

    public SignatureRecord getSignature() {
        return signature;
    }

    public String getMethodName() {
        if (signature == null) {
            return null;
        }
        return signature.methodName();
    }

    public Type getReturnType() {
        if (signature == null) {
            return null;
        }
        return signature.returnType();
    }

    public List<Type> getParameterTypes() {
        if (signature == null) {
            return null;
        }
        return signature.paramTypes();
    }

    public boolean baseClassKnown() {
        return baseClass != null;
    }

    public boolean signatureKnown() {
        return signature != null;
    }

    private MethodMetaObj(@Nullable JClass baseClass, @Nullable SignatureRecord signature) {
        this.baseClass = baseClass;
        this.signature = signature;
    }

    public static MethodMetaObj of(@Nullable JClass baseClass, @Nullable SignatureRecord signature) {
        return new MethodMetaObj(baseClass, signature);
    }

    public static final ClassType TYPE = World.get().getTypeSystem().getClassType(METHOD);

    public static final String DESC = "MethodMetaObj";

    public Set<MethodRef> search() {
        if (!baseClassKnown()) {
            throw new IllegalStateException("Cannot search for a method without a known base class.");
        }
        Set<MethodRef> result = new HashSet<>();
        JClass klass = baseClass;
        while (klass != null) {
            var candidates = klass.getDeclaredMethods();
            var filtered = candidates.stream().filter(m -> {
                // TODO: Consider private methods retrieved by getDeclaredMethod/getDeclaredMethods.
                if (!m.isPublic()) {
                    return false;
                }
                boolean returnMatched = getReturnType() == null || m.getReturnType().equals(getReturnType());
                boolean nameMatched = getMethodName() == null || m.getName().equals(getMethodName());
                boolean parameterMatched = true;
                var parameterTypes = getParameterTypes();
                if (parameterTypes != null) {
                    var mParameters = m.getParamTypes();
                    if (mParameters.size() != parameterTypes.size()) {
                        parameterMatched = false;
                    } else {
                        for (int i = 0; i < mParameters.size(); i++) {
                            if (!mParameters.get(i).equals(parameterTypes.get(i))) {
                                parameterMatched = false;
                                break;
                            }
                        }
                    }
                }
                return returnMatched && nameMatched && parameterMatched;
            }).map(JMethod::getRef).toList();
            result.addAll(filtered);
            klass = klass.getSuperClass();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodMetaObj that = (MethodMetaObj) o;
        return Objects.equals(baseClass, that.baseClass) && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseClass, signature);
    }
}
