package rho.analyser;

import rho.reader.Range;
import rho.types.ValueTypeHole;

public abstract class Expr<VT extends ValueTypeHole> {

    public final Range range;

    Expr(Range range) {
        this.range = range;
    }

    public abstract <V> V accept(ExprVisitor<V, VT> visitor);
}
