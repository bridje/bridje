package bridje.analyser;

import bridje.reader.Range;
import bridje.runtime.DataType;
import bridje.runtime.FQSymbol;
import org.pcollections.PVector;

import java.util.Objects;

import static java.util.stream.Collectors.joining;

public abstract class ActionExpr extends Expr {

    ActionExpr(Range range) {
        super(range);
    }

    public abstract <T> T accept(ActionExprVisitor<T> visitor);

    public final <T> T accept(ExprVisitor<T> visitor) {
        return visitor.accept(this);
    }

    public static class DefExpr extends ActionExpr {

        public final FQSymbol sym;
        public final PVector<LocalVar> params;
        public final ValueExpr body;

        public DefExpr(Range range, FQSymbol sym, PVector<LocalVar> params, ValueExpr body) {
            super(range);
            this.sym = sym;
            this.params = params;
            this.body = body;
        }

        @Override
        public <T> T accept(ActionExprVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefExpr defExpr = (DefExpr) o;
            return Objects.equals(sym, defExpr.sym) &&
                Objects.equals(params, defExpr.params) &&
                Objects.equals(body, defExpr.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym, body);
        }

        @Override
        public String toString() {
            return String.format("(DefExpr %s %s)",
                params.isEmpty() ? sym : String.format("(%s %s)", sym, params.stream().map(Object::toString).collect(joining(" "))),
                body);
        }
    }

    public static class DefDataExpr extends ActionExpr {

        public final DataType dataType;

        public DefDataExpr(Range range, DataType dataType) {
            super(range);
            this.dataType = dataType;
        }

        @Override
        public <V> V accept(ActionExprVisitor<V> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefDataExpr that = (DefDataExpr) o;
            return Objects.equals(dataType, that.dataType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataType);
        }

        @Override
        public String toString() {
            return String.format("(DefDataExpr %s)", dataType);
        }
    }


}
