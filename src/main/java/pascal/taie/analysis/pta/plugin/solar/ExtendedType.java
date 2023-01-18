package pascal.taie.analysis.pta.plugin.solar;

import pascal.taie.language.type.ReferenceType;
import pascal.taie.language.type.Type;
public class ExtendedType implements Type {
    private final Type type;

    public Type getType() {
        return isUnknown() ? null : type;
    }

    private ExtendedType(Type type) {
        this.type = type;
    }

    private ExtendedType() {
        this.type = null;
    }

    public static ExtendedType from(Type type) {
        return new ExtendedType(type);
    }

    private static final ExtendedType unknown = new UnknownType();

    private static class UnknownType extends ExtendedType implements ReferenceType {
        private UnknownType() {
            super();
        }
    }

    public static ExtendedType unknown() {
        return unknown;
    }

    public String getName() {
        return isUnknown() ? "<unknown>" : type.getName();
    }

    public boolean isUnknown() {
        return type == null;
    }
}
