package bridje.runtime;

import org.pcollections.PVector;

import java.util.Objects;

public abstract class DataTypeConstructor {
    public final FQSymbol sym;

    DataTypeConstructor(FQSymbol sym) {
        this.sym = sym;
    }

    public abstract <U> U accept(ConstructorVisitor<U> visitor);

    public static final class ValueConstructor extends DataTypeConstructor {

        public ValueConstructor(FQSymbol sym) {
            super(sym);
        }

        @Override
        public <U> U accept(ConstructorVisitor<U> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValueConstructor that = (ValueConstructor) o;
            return Objects.equals(sym, that.sym);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym);
        }

        @Override
        public String toString() {
            return String.format("(ValueConstructor %s)", sym);
        }
    }

    public static final class VectorConstructor extends DataTypeConstructor {

        public final PVector<Symbol> paramNames;

        public VectorConstructor(FQSymbol sym, PVector<Symbol> paramNames) {
            super(sym);
            this.paramNames = paramNames;
        }

        @Override
        public <U> U accept(ConstructorVisitor<U> visitor) {
            return visitor.visit(this);
        }
    }


}
