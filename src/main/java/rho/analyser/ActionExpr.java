package rho.analyser;

import rho.runtime.Symbol;
import rho.types.Type;

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
        public final ValueExpr<VED> body;

        public DefExpr(Symbol sym, ValueExpr<VED> body) {
            this.sym = sym;
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

    public static class TypeDefExpr<VED> extends ActionExpr<VED> {

        public final Symbol sym;
        public final Type type;

        public TypeDefExpr(Symbol sym, Type type) {
            this.sym = sym;
            this.type = type;
        }

        @Override
        public <T> T accept(ActionExprVisitor<? super VED, T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypeDefExpr<?> that = (TypeDefExpr<?>) o;
            return Objects.equals(sym, that.sym) &&
                Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym, type);
        }

        @Override
        public String toString() {
            return String.format("(TypeDefExpr %s %s)", sym, type);
        }
    }
}
