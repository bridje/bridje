package rho.analyser;

import org.pcollections.PVector;
import rho.runtime.Var;

import java.util.Objects;
import java.util.stream.Collectors;

public abstract class ValueExpr<D> extends Expr<D> {

    public final D data;

    private ValueExpr(D data) {
        this.data = data;
    }

    @Override
    public <V> V accept(ExprVisitor<V, ? super D> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ValueExprVisitor<T, ? super D> visitor);

    public static final class BoolExpr<D> extends ValueExpr<D> {
        public final boolean value;

        public BoolExpr(D data, boolean value) {
            super(data);
            this.value = value;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoolExpr boolExpr = (BoolExpr) o;
            return value == boolExpr.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return String.format("(BoolExpr %s)", value);
        }
    }

    public static final class StringExpr<D> extends ValueExpr<D> {

        public final String string;

        public StringExpr(D data, String string) {
            super(data);
            this.string = string;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringExpr that = (StringExpr) o;
            return Objects.equals(string, that.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(string);
        }

        @Override
        public String toString() {
            return String.format("(StringExpr \"%s\")", string);
        }
    }

    public static final class IntExpr<D> extends ValueExpr<D> {

        public final long num;

        public IntExpr(D data, long num) {
            super(data);
            this.num = num;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntExpr intExpr = (IntExpr) o;
            return num == intExpr.num;
        }

        @Override
        public int hashCode() {
            return Objects.hash(num);
        }

        @Override
        public String toString() {
            return String.format("(IntExpr %d)", num);
        }
    }

    public static final class VectorExpr<D> extends ValueExpr<D> {
        public final PVector<ValueExpr<D>> exprs;

        public VectorExpr(D data, PVector<ValueExpr<D>> exprs) {
            super(data);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VectorExpr that = (VectorExpr) o;
            return Objects.equals(exprs, that.exprs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exprs);
        }

        @Override
        public String toString() {
            return String.format("(VectorExpr %s)", exprs.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
    }

    public static final class SetExpr<D> extends ValueExpr<D> {
        public final PVector<ValueExpr<D>> exprs;

        public SetExpr(D data, PVector<ValueExpr<D>> exprs) {
            super(data);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SetExpr that = (SetExpr) o;
            return Objects.equals(exprs, that.exprs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exprs);
        }

        @Override
        public String toString() {
            return String.format("(SetExpr %s)", exprs.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
    }

    public static final class CallExpr<D> extends ValueExpr<D> {

        public final PVector<ValueExpr<D>> params;

        public CallExpr(D data, PVector<ValueExpr<D>> params) {
            super(data);
            this.params = params;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CallExpr callExpr = (CallExpr) o;
            return Objects.equals(params, callExpr.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(params);
        }

        @Override
        public String toString() {
            return String.format("(CallExpr (%s))", params.stream().map(Object::toString).collect(Collectors.joining(" ")));
        }
    }

    public static final class VarCallExpr<D> extends ValueExpr<D> {

        public final Var var;
        public final PVector<ValueExpr<D>> params;

        public VarCallExpr(D data, Var var, PVector<ValueExpr<D>> params) {
            super(data);
            this.var = var;
            this.params = params;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VarCallExpr varCallExpr = (VarCallExpr) o;
            return Objects.equals(var, varCallExpr.var) &&
                Objects.equals(params, varCallExpr.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(var, params);
        }

        @Override
        public String toString() {
            return String.format("(VarCallExpr (%s %s))", var, params.stream().map(Object::toString).collect(Collectors.joining(" ")));
        }
    }

    public static final class LetExpr<D> extends ValueExpr<D> {

        public static final class LetBinding<D> {
            public final LocalVar localVar;
            public final ValueExpr<D> expr;

            public LetBinding(LocalVar localVar, ValueExpr<D> expr) {
                this.localVar = localVar;
                this.expr = expr;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                LetBinding that = (LetBinding) o;
                return Objects.equals(localVar, that.localVar) &&
                    Objects.equals(expr, that.expr);
            }

            @Override
            public int hashCode() {
                return Objects.hash(localVar, expr);
            }

            @Override
            public String toString() {
                return String.format("(LetBinding %s %s)", localVar, expr);
            }
        }

        public final PVector<LetBinding<D>> bindings;
        public final ValueExpr<D> body;

        public LetExpr(D data, PVector<LetBinding<D>> bindings, ValueExpr<D> body) {
            super(data);
            this.bindings = bindings;
            this.body = body;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LetExpr letExpr = (LetExpr) o;
            return Objects.equals(bindings, letExpr.bindings) &&
                Objects.equals(body, letExpr.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bindings, body);
        }

        @Override
        public String toString() {
            return String.format("(LetExpr [%s] %s)", bindings.stream().map(Object::toString).collect(Collectors.joining(" ")), body);
        }
    }

    public static final class IfExpr<D> extends ValueExpr<D> {

        public final ValueExpr<D> testExpr;
        public final ValueExpr<D> thenExpr;
        public final ValueExpr<D> elseExpr;

        public IfExpr(D data, ValueExpr<D> testExpr, ValueExpr<D> thenExpr, ValueExpr<D> elseExpr) {
            super(data);
            this.testExpr = testExpr;
            this.thenExpr = thenExpr;
            this.elseExpr = elseExpr;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IfExpr ifExpr = (IfExpr) o;
            return Objects.equals(testExpr, ifExpr.testExpr) &&
                Objects.equals(thenExpr, ifExpr.thenExpr) &&
                Objects.equals(elseExpr, ifExpr.elseExpr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(testExpr, thenExpr, elseExpr);
        }

        @Override
        public String toString() {
            return String.format("(IfExpr %s %s %s)", testExpr, thenExpr, elseExpr);
        }
    }

    public static final class LocalVarExpr<D> extends ValueExpr<D> {

        public final LocalVar localVar;

        public LocalVarExpr(D data, LocalVar localVar) {
            super(data);
            this.localVar = localVar;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LocalVarExpr that = (LocalVarExpr) o;
            return Objects.equals(localVar, that.localVar);
        }

        @Override
        public int hashCode() {
            return Objects.hash(localVar);
        }

        @Override
        public String toString() {
            return String.format("(LocalVarExpr %s)", localVar);
        }
    }

    public static final class GlobalVarExpr<D> extends ValueExpr<D> {

        public final Var var;

        public GlobalVarExpr(D data, Var var) {
            super(data);
            this.var = var;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, ? super D> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GlobalVarExpr that = (GlobalVarExpr) o;
            return Objects.equals(var, that.var);
        }

        @Override
        public int hashCode() {
            return Objects.hash(var);
        }

        @Override
        public String toString() {
            return String.format("(GlobalVarExpr %s)", var);
        }
    }
}
