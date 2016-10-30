package rho.types;

import java.util.Objects;

public abstract class ActionType extends Type {

    public static final class DefType extends ActionType {

        public final ValueType exprType;

        public static DefType defType(ValueType exprType) {
            return new DefType(exprType);
        }

        DefType(ValueType exprType) {
            this.exprType = exprType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefType defType = (DefType) o;
            return Objects.equals(exprType, defType.exprType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exprType);
        }

        @Override
        public String toString() {
            return String.format("(DefType %s)", exprType);
        }
    }

}
