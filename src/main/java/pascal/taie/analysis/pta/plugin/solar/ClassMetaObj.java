package pascal.taie.analysis.pta.plugin.solar;

import com.sun.istack.NotNull;
import pascal.taie.World;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.type.ClassType;

import static pascal.taie.language.classes.ClassNames.CLASS;

import javax.annotation.Nullable;

public class ClassMetaObj {
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

    public ClassType getType() {
        return World.get().getTypeSystem().getClassType(CLASS);
    }

    public boolean isKnown() {
        return knownClass != null;
    }
}
