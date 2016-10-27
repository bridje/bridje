package rho.analyser;

import org.pcollections.PVector;
import rho.reader.Form;
import rho.reader.Range;
import rho.runtime.Var;

import java.util.Objects;
import java.util.stream.Collectors;

public abstract class ValueExpr extends Expr {

    private ValueExpr(Range range) {
        super(range);
    }

    @Override
    public <T> T accept(ExprVisitor<T> visitor) {
        return visitor.accept(this);
    }

    public abstract <T> T accept(ValueExprVisitor<T> visitor);

    public static final class BoolExpr extends ValueExpr {
        public final boolean value;

        public static BoolExpr fromForm(Form.BoolForm form) {
            return new BoolExpr(form.range, form.value);
        }

        public static BoolExpr boolExpr(boolean value) {
            return new BoolExpr(null, value);
        }

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

        public static StringExpr stringExpr(String string) {
            return new StringExpr(null, string);
        }

        public static StringExpr fromForm(Form.StringForm form) {
            return new StringExpr(form.range, form.string);
        }

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

        public static IntExpr fromForm(Form.IntForm form) {
            return new IntExpr(form.range, form.num);
        }

        public static IntExpr intExpr(long num) {
            return new IntExpr(null, num);
        }

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

        public static VectorExpr vectorExpr(PVector<ValueExpr> exprs) {
            return vectorExpr(null, exprs);
        }

        public static VectorExpr vectorExpr(Range range, PVector<ValueExpr> exprs) {
            return new VectorExpr(range, exprs);
        }

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
            return String.format("(VectorExpr %s)", exprs.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
    }

    public static final class SetExpr extends ValueExpr {
        public final PVector<ValueExpr> exprs;

        public static SetExpr setExpr(PVector<ValueExpr> exprs) {
            return setExpr(null, exprs);
        }

        public static SetExpr setExpr(Range range, PVector<ValueExpr> exprs) {
            return new SetExpr(range, exprs);
        }

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
            return String.format("(SetExpr %s)", exprs.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
    }

    public static final class CallExpr extends ValueExpr {

        public final Var var;
        public final PVector<ValueExpr> params;

        public static CallExpr callExpr(Var var, PVector<ValueExpr> params) {
            return new CallExpr(null, var, params);
        }

        public CallExpr(Range range, Var var, PVector<ValueExpr> params) {
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
            CallExpr callExpr = (CallExpr) o;
            return Objects.equals(var, callExpr.var) &&
                Objects.equals(params, callExpr.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(var, params);
        }

        @Override
        public String toString() {
            return String.format("(CallExpr (%s %s))", var, params.stream().map(Object::toString).collect(Collectors.joining(" ")));
        }
    }

    public static final class LetExpr extends ValueExpr {

        public static final class LetBinding {
            public final Range range;
            public final LocalVar localVar;
            public final ValueExpr expr;

            public static LetBinding letBinding(LocalVar symbol, ValueExpr expr) {
                return new LetBinding(null, symbol, expr);
            }

            public LetBinding(Range range, LocalVar localVar, ValueExpr expr) {
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

        public final PVector<LetBinding> bindings;
        public final ValueExpr body;

        public static LetExpr letExpr(PVector<LetBinding> bindings, ValueExpr body) {
            return new LetExpr(null, bindings, body);
        }

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
            return String.format("(LetExpr [%s] %s)", bindings.stream().map(Object::toString).collect(Collectors.joining(" ")), body);
        }
    }

    public static final class IfExpr extends ValueExpr {

        public final ValueExpr testExpr;
        public final ValueExpr thenExpr;
        public final ValueExpr elseExpr;

        public static IfExpr ifExpr(ValueExpr testExpr, ValueExpr thenExpr, ValueExpr elseExpr) {
            return new IfExpr(null, testExpr, thenExpr, elseExpr);
        }

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

        public static LocalVarExpr localVarExpr(LocalVar localVar) {
            return new LocalVarExpr(null, localVar);
        }

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
}
