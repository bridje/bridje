package bridje.runtime;

import bridje.types.Type;
import org.pcollections.Empty;
import org.pcollections.PMap;

import static bridje.Util.toPMap;

public class NSEnv {
    public final NS ns;

    public final PMap<Symbol, FQSymbol> vars;
    public final PMap<Symbol, FQSymbol> dataTypes;
    public final PMap<Symbol, NS> aliases;

    public static NSEnv empty(NS ns) {
        return new NSEnv(ns, Empty.map(), Empty.map(), Empty.map());
    }

    public static NSEnv fromDeclaration(NS ns, PMap<Symbol, NS> aliases) {
        return new NSEnv(ns, Empty.map(), Empty.map(), aliases);
    }

    public NSEnv(NS ns, PMap<Symbol, FQSymbol> vars, PMap<Symbol, FQSymbol> dataTypes, PMap<Symbol, NS> aliases) {
        this.ns = ns;
        this.vars = vars;
        this.dataTypes = dataTypes;
        this.aliases = aliases;
    }

    public NSEnv withVar(FQSymbol fqSymbol) {
        return new NSEnv(ns, vars.plus(fqSymbol.symbol, fqSymbol), dataTypes, aliases);
    }

    public NSEnv withDataType(DataType<Type> dataType) {
        return new NSEnv(ns,
            vars.plusAll(dataType.constructors.stream().collect(toPMap(c -> c.sym.symbol, c -> c.sym))),
            dataTypes.plus(dataType.sym.symbol, dataType.sym), aliases);
    }
}
