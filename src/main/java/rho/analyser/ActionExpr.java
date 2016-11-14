package rho.analyser;

import org.pcollections.PVector;
import rho.runtime.Symbol;

import java.util.Objects;

public abstract class ActionExpr<VED> extends Expr<VED> {
    private ActionExpr() {
    }

    @Override
    public <T> T accept(ExprVisitor<? super VED, T> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ActionExprVisitor<? super VED, T> visitor);

    public static class DefExpr<VED> extends ActionExpr<VED> {

        public final Symbol sym;
        public final PVector<LocalVar> params;
        public final ValueExpr<VED> body;

        public DefExpr(Symbol sym, PVector<LocalVar> params, ValueExpr<VED> body) {
            this.sym = sym;
            this.params = params;
            this.body = body;
        }

        @Override
        public <T> T accept(ActionExprVisitor<? super VED, T> visitor) {
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
            return Objects.hash(sym, params, body);
        }

        @Override
        public String toString() {
            return String.format("(DefExpr %s %s)", sym, body);
        }
    }
}
