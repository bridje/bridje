package rho.analyser;

import rho.reader.Range;
import rho.runtime.Symbol;
import rho.types.ValueTypeHole;

import java.util.Objects;

public abstract class ActionExpr<VT extends ValueTypeHole> extends Expr<VT> {
    private ActionExpr(Range range) {
        super(range);
    }

    @Override
    public <T> T accept(ExprVisitor<T, VT> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ActionExprVisitor<T, VT> visitor);

    public static class DefExpr<VT extends ValueTypeHole> extends ActionExpr<VT> {

        public final Symbol sym;
        public final ValueExpr<VT> body;

        public DefExpr(Range range, Symbol sym, ValueExpr<VT> body) {
            super(range);
            this.sym = sym;
            this.body = body;
        }

        @Override
        public <T> T accept(ActionExprVisitor<T, VT> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefExpr defExpr = (DefExpr) o;
            return Objects.equals(sym, defExpr.sym) &&
                Objects.equals(body, defExpr.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym, body);
        }

        @Override
        public String toString() {
            return String.format("(DefExpr %s %s)", sym, body);
        }
    }
}
