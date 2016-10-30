package rho.analyser;

import rho.reader.Range;
import rho.runtime.Symbol;

import java.util.Objects;

public abstract class ActionExpr extends Expr {
    ActionExpr(Range range) {
        super(range);
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ActionExprVisitor<T> visitor);

    public static class DefExpr extends ActionExpr {

        public final Symbol sym;
        public final ValueExpr body;

        public static DefExpr defExpr(Symbol sym, ValueExpr body) {
            return new DefExpr(null, sym, body);
        }

        public DefExpr(Range range, Symbol sym, ValueExpr body) {
            super(range);
            this.sym = sym;
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
