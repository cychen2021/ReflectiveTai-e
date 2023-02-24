package pascal.taie.analysis.pta.plugin.solar;

import com.sun.istack.NotNull;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

import static pascal.taie.language.classes.ClassNames.CLASS;

import javax.annotation.Nullable;
import java.util.Objects;

class ClassMetaObj {
    private final JClass knownClass;

    private final Type type;

    private ClassMetaObj(@Nullable JClass klass, Type type) {
        this.knownClass = klass;
        this.type = type;
    }

    public Type getType() {
        return type;
    }


    static class Builder {
        private final Type META_OBJ_TYPE;

        public Builder(TypeSystem typeSystem) {
            this.META_OBJ_TYPE = typeSystem.getType(CLASS);
        }

        private ClassMetaObj UNKNOWN = null;

        private ClassMetaObj known(@NotNull JClass klass) {
            return new ClassMetaObj(klass, META_OBJ_TYPE);
        }

        private ClassMetaObj unknown() {
            if (UNKNOWN == null) {
                UNKNOWN = new ClassMetaObj(null, META_OBJ_TYPE);
            }
            return UNKNOWN;
        }
        public ClassMetaObj build(@Nullable JClass jClass) {
            if (jClass == null) {
                return unknown();
            }
            return known(jClass);
        }
    }

    private static final String DESC = "ClassMetaObj";

    public String getDesc() {
        return DESC;
    }

    public boolean isKnown() {
        return knownClass != null;
    }

    public JClass getJClass() {
        return knownClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassMetaObj metaObj = (ClassMetaObj) o;
        return Objects.equals(type, metaObj.type) && Objects.equals(knownClass, metaObj.knownClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, knownClass);
    }

    @Override
    public String toString() {
        return "ClassMetaObj{" +
                "knownClass=" + knownClass +
                '}';
    }
}
