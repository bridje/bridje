package bridje.runtime;

import bridje.types.Type;
import org.pcollections.PMap;

import static bridje.Util.toPMap;

public class NSEnv {
    public final NS ns;

    public final PMap<Symbol, FQSymbol> vars;
    public final PMap<Symbol, FQSymbol> dataTypes;

    public NSEnv(NS ns, PMap<Symbol, FQSymbol> vars, PMap<Symbol, FQSymbol> dataTypes) {
        this.ns = ns;
        this.vars = vars;
        this.dataTypes = dataTypes;
    }

    public NSEnv withVar(FQSymbol fqSymbol) {
        return new NSEnv(ns, vars.plus(fqSymbol.symbol, fqSymbol), dataTypes);
    }

    public NSEnv withDataType(DataType<Type> dataType) {
        return new NSEnv(ns,
            vars.plusAll(dataType.constructors.stream().collect(toPMap(c -> c.sym.symbol, c -> c.sym))),
            dataTypes.plus(dataType.sym.symbol, dataType.sym));
    }
}
