package bridje.analyser;

import bridje.reader.Range;

public abstract class Expr {

    public final Range range;

    Expr(Range range) {
        this.range = range;
    }

    public abstract <V> V accept(ExprVisitor<V> visitor);
}
