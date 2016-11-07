package rho.analyser;

import org.pcollections.PVector;
import rho.reader.Range;
import rho.runtime.Var;
import rho.types.ValueTypeHole;

import java.util.Objects;
import java.util.stream.Collectors;

public abstract class ValueExpr<VT extends ValueTypeHole> extends Expr<VT> {

    public final VT type;

    private ValueExpr(Range range, VT type) {
        super(range);
        this.type = type;
    }

    @Override
    public <V> V accept(ExprVisitor<V, VT> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ValueExprVisitor<T, VT> visitor);

    public static final class BoolExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {
        public final boolean value;

        public BoolExpr(Range range, VT type, boolean value) {
            super(range, type);
            this.value = value;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class StringExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public final String string;

        public StringExpr(Range range, VT type, String string) {
            super(range, type);
            this.string = string;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class IntExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public final long num;

        public IntExpr(Range range, VT type, long num) {
            super(range, type);
            this.num = num;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class VectorExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {
        public final PVector<ValueExpr<VT>> exprs;

        public VectorExpr(Range range, VT type, PVector<ValueExpr<VT>> exprs) {
            super(range, type);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class SetExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {
        public final PVector<ValueExpr<VT>> exprs;

        public SetExpr(Range range, VT type, PVector<ValueExpr<VT>> exprs) {
            super(range, type);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class CallExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public final PVector<ValueExpr<VT>> params;

        public CallExpr(Range range, VT type, PVector<ValueExpr<VT>> params) {
            super(range, type);
            this.params = params;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class VarCallExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public final Var var;
        public final PVector<ValueExpr<VT>> params;

        public VarCallExpr(Range range, VT type, Var var, PVector<ValueExpr<VT>> params) {
            super(range, type);
            this.var = var;
            this.params = params;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class LetExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public static final class LetBinding<VT extends ValueTypeHole> {
            public final Range range;
            public final LocalVar<VT> localVar;
            public final ValueExpr<VT> expr;

            public LetBinding(Range range, LocalVar<VT> localVar, ValueExpr<VT> expr) {
                this.range = range;
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

        public final PVector<LetBinding<VT>> bindings;
        public final ValueExpr<VT> body;

        public LetExpr(Range range, VT type, PVector<LetBinding<VT>> bindings, ValueExpr<VT> body) {
            super(range, type);
            this.bindings = bindings;
            this.body = body;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class IfExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public final ValueExpr<VT> testExpr;
        public final ValueExpr<VT> thenExpr;
        public final ValueExpr<VT> elseExpr;

        public IfExpr(Range range, VT type, ValueExpr<VT> testExpr, ValueExpr<VT> thenExpr, ValueExpr<VT> elseExpr) {
            super(range, type);
            this.testExpr = testExpr;
            this.thenExpr = thenExpr;
            this.elseExpr = elseExpr;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class LocalVarExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public final LocalVar localVar;

        public LocalVarExpr(Range range, VT type, LocalVar localVar) {
            super(range, type);
            this.localVar = localVar;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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

    public static final class GlobalVarExpr<VT extends ValueTypeHole> extends ValueExpr<VT> {

        public final Var var;

        public GlobalVarExpr(Range range, VT type, Var var) {
            super(range, type);
            this.var = var;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T, VT> visitor) {
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
