package rho.analyser;

import rho.reader.Form;
import rho.reader.Range;

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
    }

}
