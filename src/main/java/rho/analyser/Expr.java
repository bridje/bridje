package rho.analyser;

import rho.reader.Range;

public abstract class Expr {

    public final Range range;

    Expr(Range range) {
        this.range = range;
    }

    public abstract <T> T accept(ExprVisitor<T> visitor);
}
