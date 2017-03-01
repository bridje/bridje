package bridje.analyser;

import bridje.reader.Range;

public abstract class Expr {
    public final Range range;

    Expr(Range range) {
        this.range = range;
    }

    public static final class StringExpr extends Expr {
        public final String value;

        public StringExpr(Range range, String value) {
            super(range);
            this.value = value;
        }
    }
}
