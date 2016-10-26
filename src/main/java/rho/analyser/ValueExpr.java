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
}
