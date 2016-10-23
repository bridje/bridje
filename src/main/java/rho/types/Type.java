package rho.types;

public abstract class Type {
    private Type() {
    }

    public static final Type STRING_TYPE = new Type() {
        @Override
        public String toString() {
            return "Str";
        }
    };

    public static final Type INT_TYPE = new Type() {
        @Override
        public String toString() {
            return "Int";
        }
    };
}
