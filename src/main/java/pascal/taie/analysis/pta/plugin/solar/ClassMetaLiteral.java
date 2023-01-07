package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.World;
import pascal.taie.ir.exp.ClassLiteral;
import pascal.taie.ir.exp.ExpVisitor;
import pascal.taie.ir.exp.ReferenceLiteral;
import pascal.taie.language.type.ReferenceType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Pair;

import static pascal.taie.language.classes.ClassNames.CLASS;

public class ClassMetaLiteral implements ReferenceLiteral {
    private final ClassLiteral classLiteral;

    private ClassMetaLiteral(ClassLiteral classLiteral) {
        this.classLiteral = classLiteral;
    }

    private ClassMetaLiteral() {
        this.classLiteral = null;
    }

    public boolean isUnknown() {
        return classLiteral == null;
    }

    public static ClassMetaLiteral from(Type value) {
        return new ClassMetaLiteral(ClassLiteral.get(value));
    }

    public ClassLiteral getClassLiteral() {
        return isUnknown() ? null : classLiteral;
    }

    private static final ClassMetaLiteral unknown = new ClassMetaLiteral();

    public static ClassMetaLiteral unknown() {
        return unknown;
    }

    @Override
    public <T> T accept(ExpVisitor<T> visitor) {
        return null;
    }

    @Override
    public ReferenceType getType() {
        return World.get().getTypeSystem().getClassType(CLASS);
    }

    @Override
    public int hashCode() {
        Pair<Integer, Integer> pair =
                isUnknown() ? new Pair<>(0, 0) : new Pair<>(1, classLiteral.hashCode());
        return pair.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassMetaLiteral that = (ClassMetaLiteral) o;
        if (isUnknown()) {
            return that.isUnknown();
        }
        return classLiteral.equals(that.classLiteral);
    }
}
