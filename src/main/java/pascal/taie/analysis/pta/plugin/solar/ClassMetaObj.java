package pascal.taie.analysis.pta.plugin.solar;

import com.sun.istack.NotNull;
import pascal.taie.World;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.type.ClassType;

import static pascal.taie.language.classes.ClassNames.CLASS;

import javax.annotation.Nullable;
import java.util.Objects;

class ClassMetaObj {
    private final JClass knownClass;

    private static final ClassMetaObj UNKNOWN = new ClassMetaObj(null);

    private ClassMetaObj(@Nullable JClass klass) {
        this.knownClass = klass;
    }

    public static ClassMetaObj unknown() {
        return UNKNOWN;
    }

    public static ClassMetaObj known(@NotNull JClass klass) {
        if (klass == null) {
            throw new IllegalArgumentException("The class represented by a known ClassMetaObj cannot be null.");
        }
        return new ClassMetaObj(klass);
    }

    public static final ClassType TYPE = World.get().getTypeSystem().getClassType(CLASS);

    public static final String DESC = "ClassMetaObj";

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
        return Objects.equals(knownClass, metaObj.knownClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(knownClass);
    }

    @Override
    public String toString() {
        return "ClassMetaObj{" +
                "knownClass=" + knownClass +
                '}';
    }
}
