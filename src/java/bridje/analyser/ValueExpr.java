package bridje.analyser;

import bridje.reader.Range;
import bridje.runtime.Var;
import org.pcollections.PVector;

import java.util.Objects;

import static java.util.stream.Collectors.joining;

public abstract class ValueExpr extends Expr {

    ValueExpr(Range range) {
        super(range);
    }

    public final <T> T accept(ExprVisitor<T> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ValueExprVisitor<T> visitor);

    public static final class BoolExpr extends ValueExpr {
        public final boolean value;

        public BoolExpr(Range range, boolean value) {
            super(range);
            this.value = value;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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

    public static final class StringExpr extends ValueExpr {

        public final String string;

        public StringExpr(Range range, String string) {
            super(range);
            this.string = string;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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

    public static final class IntExpr extends ValueExpr {

        public final long num;

        public IntExpr(Range range, long num) {
            super(range);
            this.num = num;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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

    public static final class VectorExpr extends ValueExpr {
        public final PVector<ValueExpr> exprs;

        public VectorExpr(Range range, PVector<ValueExpr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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
            return String.format("(VectorExpr %s)", exprs.stream().map(Object::toString).collect(joining(", ")));
        }
    }

    public static final class SetExpr extends ValueExpr {
        public final PVector<ValueExpr> exprs;

        public SetExpr(Range range, PVector<ValueExpr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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
            return String.format("(SetExpr %s)", exprs.stream().map(Object::toString).collect(joining(", ")));
        }
    }

    public static final class CallExpr extends ValueExpr {

        public final PVector<ValueExpr> exprs;

        public CallExpr(Range range, PVector<ValueExpr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CallExpr callExpr = (CallExpr) o;
            return Objects.equals(exprs, callExpr.exprs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exprs);
        }

        @Override
        public String toString() {
            return String.format("(CallExpr (%s))", exprs.stream().map(Object::toString).collect(joining(" ")));
        }
    }

    public static final class VarCallExpr extends ValueExpr {

        public final Var var;
        public final PVector<ValueExpr> params;

        public VarCallExpr(Range range, Var var, PVector<ValueExpr> params) {
            super(range);
            this.var = var;
            this.params = params;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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
            return String.format("(VarCallExpr (%s %s))", var, params.stream().map(Object::toString).collect(joining(" ")));
        }
    }

    public static final class LetExpr extends ValueExpr {

        public static final class LetBinding {
            public final LocalVar localVar;
            public final ValueExpr expr;

            public LetBinding(LocalVar localVar, ValueExpr expr) {
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

        public final PVector<LetBinding> bindings;
        public final ValueExpr body;

        public LetExpr(Range range, PVector<LetBinding> bindings, ValueExpr body) {
            super(range);
            this.bindings = bindings;
            this.body = body;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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
            return String.format("(LetExpr [%s] %s)", bindings.stream().map(Object::toString).collect(joining(" ")), body);
        }
    }

    public static final class IfExpr extends ValueExpr {

        public final ValueExpr testExpr;
        public final ValueExpr thenExpr;
        public final ValueExpr elseExpr;

        public IfExpr(Range range, ValueExpr testExpr, ValueExpr thenExpr, ValueExpr elseExpr) {
            super(range);
            this.testExpr = testExpr;
            this.thenExpr = thenExpr;
            this.elseExpr = elseExpr;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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

    public static final class LocalVarExpr extends ValueExpr {

        public final LocalVar localVar;

        public LocalVarExpr(Range range, LocalVar localVar) {
            super(range);
            this.localVar = localVar;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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

    public static final class GlobalVarExpr extends ValueExpr {

        public final Var var;

        public GlobalVarExpr(Range range, Var var) {
            super(range);
            this.var = var;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
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

    public static final class FnExpr extends ValueExpr {

        public final PVector<LocalVar> params;
        public final ValueExpr body;

        public FnExpr(Range range, PVector<LocalVar> params, ValueExpr body) {
            super(range);
            this.params = params;
            this.body = body;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FnExpr fnExpr = (FnExpr) o;
            return Objects.equals(params, fnExpr.params) &&
                Objects.equals(body, fnExpr.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(params, body);
        }

        @Override
        public String toString() {
            return String.format("(FnExpr (%s) %s)", params.stream().map(Object::toString).collect(joining(" ")), body);
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

}
