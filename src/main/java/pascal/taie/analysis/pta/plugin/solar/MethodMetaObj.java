package pascal.taie.analysis.pta.plugin.solar;

import com.sun.istack.NotNull;
import pascal.taie.World;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.type.ReferenceType;
import pascal.taie.language.type.Type;

import javax.annotation.Nullable;

import static pascal.taie.language.classes.ClassNames.METHOD;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MethodMetaObj {
    private Boolean isStatic;

    private final JClass baseClass;
    private final String methodName;
    private final Type returnType;
    private final List<Type> parameterTypes;

    private final MethodRef methodRef;

    public boolean isKnown() {
        return methodRef != null;
    }

    public boolean methodNameKnown() {
        return !isKnown() && methodName != null;
    }

    public boolean returnTypeKnown() {
        return !isKnown() && returnType != null;
    }

    public boolean parameterTypesKnown() {
        if (!isKnown() || parameterTypes == null) {
            return false;
        }
        for (var t: parameterTypes) {
            if (t == null) {
                return false;
            }
        }
        return true;
    }

    public boolean baseClassKnown() {
        return !isKnown() && baseClass != null;
    }

    private MethodMetaObj(@NotNull MethodRef methodRef) {
        this.methodRef = methodRef;
        this.methodName = null;
        this.returnType = null;
        this.parameterTypes = null;
        this.baseClass = null;
        this.isStatic = null;
    }

    private MethodMetaObj(@Nullable JClass baseClass, @Nullable String name,
                          @Nullable List<Type> parameterTypes, @Nullable Type returnType,
                          boolean isStatic) {
        this.methodRef = null;
        this.methodName = name;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.baseClass = baseClass;
        this.isStatic = isStatic;
    }

    public boolean isStatic() {
        if (!isKnown()) {
            assert isStatic != null;
            return isStatic;
        } else {
            return methodRef.isStatic();
        }
    }

    public static MethodMetaObj unknown(@Nullable JClass baseClass,
                                        @Nullable String name,
                                        @Nullable List<Type> parameterTypes,
                                        @Nullable Type returnType) {
        if (baseClass != null && name != null && parameterTypes != null && returnType != null) {
            throw new IllegalArgumentException("Cannot create an unknown MethodMetaObj with all known information.");
        }

        return new MethodMetaObj(baseClass, name, parameterTypes, returnType, false);
    }

    public static MethodMetaObj unknownStatic(@Nullable JClass baseClass,
                                        @Nullable String name,
                                        @Nullable List<Type> parameterTypes,
                                        @Nullable Type returnType) {
        if (baseClass != null && name != null && parameterTypes != null && returnType != null) {
            throw new IllegalArgumentException("Cannot create an unknown MethodMetaObj with all known information.");
        }

        return new MethodMetaObj(baseClass, name, parameterTypes, returnType, true);
    }
    public ReferenceType getType() {
        return World.get().getTypeSystem().getClassType(METHOD);
    }
}
