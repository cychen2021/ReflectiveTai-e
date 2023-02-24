package pascal.taie.analysis.pta.plugin.solar;

import com.sun.istack.NotNull;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

import javax.annotation.Nullable;

import java.util.Objects;

import static pascal.taie.language.classes.ClassNames.OBJECT;

class LazyObj {
    private final Type baseType;
    private final Type type;

    private final static String LAZY_OBJ_DESC = "LazyObj";

    private LazyObj(@Nullable Type baseType, Type type) {
        this.baseType = baseType;
        this.type = type;
    }

    public Type getBaseType() {
        return baseType;
    }

    public Type getType() {
        return type;
    }

    public boolean isKnown() {
        return baseType != null;
    }

    public String getDesc() {
        return LAZY_OBJ_DESC;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LazyObj lazyObj = (LazyObj) o;
        return Objects.equals(baseType, lazyObj.baseType) && Objects.equals(type, lazyObj.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseType, type);
    }

    @Override
    public String toString() {
        return isKnown() ? "TYPE_KNOWN" : "TYPE_UNKNOWN";
    }

    static class Builder {
        private final Type UNKNOWN_LAZY_OBJ_TYPE;

        public Builder(TypeSystem typeSystem) {
            UNKNOWN_LAZY_OBJ_TYPE = typeSystem.getType(OBJECT);
        }

        private LazyObj UNKNOWN = null;

        public LazyObj known(@NotNull Type baseType) {
            return new LazyObj(baseType, baseType);
        }

        public LazyObj unknown() {
            if (UNKNOWN == null) {
                UNKNOWN = new LazyObj(null, UNKNOWN_LAZY_OBJ_TYPE);
            }
            return UNKNOWN;
        }

        public Type getUnknownType() {
            return UNKNOWN_LAZY_OBJ_TYPE;
        }
    }
}

