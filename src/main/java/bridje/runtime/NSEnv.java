package bridje.runtime;

import org.pcollections.Empty;
import org.pcollections.PMap;

public class NSEnv {
    public static final NSEnv EMPTY = new NSEnv(Empty.map());

    public final PMap<Symbol, Var> vars;

    public NSEnv(PMap<Symbol, Var> vars) {
        this.vars = vars;
    }

    public NSEnv withVar(Symbol sym, Var var) {
        return new NSEnv(vars.plus(sym, var));
    }
}
