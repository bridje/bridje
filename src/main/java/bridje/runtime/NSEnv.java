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
    public final PMap<Symbol, FQSymbol> refers;
    public final PMap<Symbol, Class<?>> imports;

    public static NSEnv empty(NS ns) {
        return new NSEnv(ns, Empty.map(), Empty.map(), Empty.map(), Empty.map(), Empty.map());
    }

    public static NSEnv fromDeclaration(NS ns, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers, PMap<Symbol, Class<?>> imports) {
        return new NSEnv(ns, Empty.map(), Empty.map(), aliases, refers, imports);
    }

    public NSEnv(NS ns, PMap<Symbol, FQSymbol> vars, PMap<Symbol, FQSymbol> dataTypes, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers, PMap<Symbol, Class<?>> imports) {
        this.ns = ns;
        this.vars = vars;
        this.dataTypes = dataTypes;
        this.aliases = aliases;
        this.refers = refers;
        this.imports = imports;
    }

    public NSEnv withVar(FQSymbol fqSymbol) {
        return new NSEnv(ns, vars.plus(fqSymbol.symbol, fqSymbol), dataTypes, aliases, refers, imports);
    }

    public NSEnv withDataType(DataType<Type> dataType) {
        return new NSEnv(ns,
            vars.plusAll(dataType.constructors.stream().collect(toPMap(c -> c.sym.symbol, c -> c.sym))),
            dataTypes.plus(dataType.sym.symbol, dataType.sym), aliases, refers, imports);
    }
}
