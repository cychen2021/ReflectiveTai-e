package pascal.taie.analysis.pta.plugin.solar;

import com.sun.istack.NotNull;
import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JField;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static pascal.taie.language.classes.ClassNames.FIELD;

class FieldMetaObj {
    private final JField jField;

    public boolean isManuallyResolved() {
        return jField != null;
    }

    record SignatureRecord(String fieldName, Type fieldType) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SignatureRecord signature = (SignatureRecord) o;
            return Objects.equals(fieldName, signature.fieldName)
                    && Objects.equals(fieldType, signature.fieldType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldName, fieldType);
        }

        public static SignatureRecord of(@Nullable String methodName, @Nullable Type fieldType) {
            return new SignatureRecord(methodName, fieldType);
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

    public String getFieldName() {
        if (signature == null) {
            return null;
        }
        return signature.fieldName();
    }

    public Type getFieldType() {
        if (signature == null) {
            return null;
        }
        return signature.fieldType();
    }

    public boolean baseClassIsKnown() {
        return baseClass != null;
    }

    public boolean signatureIsKnown() {
        return signature != null;
    }

    private FieldMetaObj(@Nullable JClass baseClass, @Nullable SignatureRecord signature, Type type) {
        this.baseClass = baseClass;
        this.signature = signature;
        this.type = type;
        this.jField = null;
    }

    private FieldMetaObj(@NotNull JField jField, Type type) {
        this.baseClass = jField.getDeclaringClass();
        this.signature = SignatureRecord.of(jField.getName(), jField.getType());
        this.type = type;
        this.jField = jField;
    }

    static class Builder {
        private final Type FIELD_META_OBJ_TYPE;
        public Builder(TypeSystem typeSystem) {
            this.FIELD_META_OBJ_TYPE = typeSystem.getType(FIELD);
        }

        public FieldMetaObj build(@Nullable JClass baseClass, @Nullable SignatureRecord signature) {
            return new FieldMetaObj(baseClass, signature, FIELD_META_OBJ_TYPE);
        }

        public FieldMetaObj build(JField field) {
            return new FieldMetaObj(field, FIELD_META_OBJ_TYPE);
        }
    }

    private final Type type;

    private static final String DESC = "FieldMetaObj";

    public String getDesc() {
        return DESC;
    }

    public Type getType() {
        return type;
    }


    public Set<FieldRef> search() {
        if (isManuallyResolved()) {
            return Set.of(jField.getRef());
        }

        if (!baseClassIsKnown()) {
            throw new IllegalStateException("Cannot search for a method without a known base class.");
        }
        Set<FieldRef> result = new HashSet<>();
        JClass klass = baseClass;
        while (klass != null) {
            var candidates = klass.getDeclaredFields();
            var filtered = candidates.stream().filter(f -> {
                // TODO: Consider private fields retrieved by getDeclaredField/getDeclaredFields.
                if (!f.isPublic()) {
                    return false;
                }
                boolean typeMatched = getFieldType() == null || f.getType().equals(getFieldType());
                boolean nameMatched = getFieldName() == null || f.getName().equals(getFieldName());
                return typeMatched && nameMatched;
            }).map(JField::getRef).toList();
            result.addAll(filtered);
            klass = klass.getSuperClass();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldMetaObj that = (FieldMetaObj) o;
        return Objects.equals(type, that.type) && Objects.equals(baseClass, that.baseClass) && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, baseClass, signature);
    }

    @Override
    public String toString() {
        return "FieldMetaObj{" +
                "baseClass=" + baseClass +
                ", signature=" + signature +
                '}';
    }
}
