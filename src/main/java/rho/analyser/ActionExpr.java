package rho.analyser;

import rho.reader.Range;
import rho.runtime.Symbol;

import java.util.Objects;

public abstract class ActionExpr<VED> extends Expr<VED> {
    private ActionExpr(Range range) {
        super();
    }

    @Override
    public <T> T accept(ExprVisitor<T, ? super VED> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ActionExprVisitor<T, ? super VED> visitor);

    public static class DefExpr<VED> extends ActionExpr<VED> {

        public final Symbol sym;
        public final ValueExpr<VED> body;

        public DefExpr(Range range, Symbol sym, ValueExpr<VED> body) {
            super(range);
            this.sym = sym;
            this.body = body;
        }

        @Override
        public <T> T accept(ActionExprVisitor<T, ? super VED> visitor) {
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
