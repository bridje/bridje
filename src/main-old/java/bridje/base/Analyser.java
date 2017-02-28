package bridje.base;

import bridje.analyser.Expr;
import bridje.reader.Form;
import bridje.runtime.Env;
import bridje.runtime.NS;

public class Analyser implements Lang {

    @Override
    public Expr apply(Env env, NS currentNS, Form form) {
        return new Expr.StringExpr(form.range, "Foo");
    }
}
