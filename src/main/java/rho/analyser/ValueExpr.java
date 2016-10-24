package rho.analyser;

import org.pcollections.PVector;
import rho.reader.Form;
import rho.reader.Range;

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
            return visitor.accept(this);
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
            return visitor.accept(this);
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
        public final PVector<Expr> exprs;

        public static Expr vectorExpr(PVector<Expr> exprs) {
            return vectorExpr(null, exprs);
        }

        public static Expr vectorExpr(Range range, PVector<Expr> exprs) {
            return new VectorExpr(range, exprs);
        }

        public VectorExpr(Range range, PVector<Expr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
            return visitor.accept(this);
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
        public final PVector<Expr> exprs;

        public static Expr setExpr(PVector<Expr> exprs) {
            return setExpr(null, exprs);
        }

        public static Expr setExpr(Range range, PVector<Expr> exprs) {
            return new SetExpr(range, exprs);
        }

        public SetExpr(Range range, PVector<Expr> exprs) {
            super(range);
            this.exprs = exprs;
        }

        @Override
        public <T> T accept(ValueExprVisitor<T> visitor) {
            return visitor.accept(this);
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
}