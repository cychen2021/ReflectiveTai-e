package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.World;
import pascal.taie.ir.exp.ExpVisitor;
import pascal.taie.ir.exp.ReferenceLiteral;
import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.language.type.ReferenceType;
import static pascal.taie.language.classes.ClassNames.FIELD;

import java.util.Optional;

public class FieldLiteral implements ReferenceLiteral {
    private Boolean isStatic;

    private final ExtendedJClass baseClass;

    private final FieldRef fieldRef;

    private final Optional<String> fieldName;

    private final ExtendedType fieldType;

    public boolean signatureUnknown() {
        if (!isUnknown()) {
            return false;
        }
        return methodNameUnknown() || fieldTypeUnknown();
    }

    public boolean isUnknown() {
        return fieldRef == null;
    }

    public boolean methodNameUnknown() {
        return isUnknown() && fieldName.isEmpty();
    }

    public boolean fieldTypeUnknown() {
        return isUnknown() && fieldType.isUnknown();
    }

    public boolean baseClassUnknown() {
        return isUnknown() && baseClass.isUnknown();
    }

    private FieldLiteral(FieldRef methodRef) {
        this.fieldRef = methodRef;
        this.fieldName = null;
        this.fieldType = null;
        this.baseClass = null;
        this.isStatic = null;
    }

    private FieldLiteral(ExtendedJClass baseClass, Optional<String> name,
                         ExtendedType fieldType, boolean isStatic) {
        this.fieldRef = null;
        this.fieldName = name;
        this.fieldType = fieldType;
        this.baseClass = baseClass;
        this.isStatic = isStatic;
    }

    public boolean isStatic() {
        if (isUnknown()) {
            assert isStatic != null;
            return isStatic;
        }
        return fieldRef.isStatic();
    }

    public FieldRef getFieldRef() {
        return isUnknown() ? null : fieldRef;
    }

    @Override
    public <T> T accept(ExpVisitor<T> visitor) {
        return null;
    }

    @Override
    public ReferenceType getType() {
        return World.get().getTypeSystem().getClassType(FIELD);
    }

    public static FieldLiteral from(ExtendedJClass baseClass, Optional<String> name,
                                    ExtendedType fieldType, boolean isStatic) {
        if (baseClass.isUnknown() || name.isEmpty() || fieldType.isUnknown()) {
            return new FieldLiteral(baseClass, name, fieldType, isStatic);
        }
        FieldRef fieldRef =
                FieldRef.get(baseClass.getJClass(), name.get(), fieldType.getType(), isStatic);
        return new FieldLiteral(fieldRef);
    }
}
