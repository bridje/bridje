package bridje.analyser;

import bridje.runtime.FQSymbol;
import bridje.runtime.NS;
import bridje.runtime.Symbol;
import org.pcollections.Empty;
import org.pcollections.PMap;

public class NSDeclaration {
    public final NS ns;
    public final PMap<Symbol, NS> aliases;
    public final PMap<Symbol, FQSymbol> refers;
    public final PMap<Symbol, Class<?>> imports;

    public NSDeclaration(NS ns) {
        this(ns, Empty.map(), Empty.map(), Empty.map());
    }

    public NSDeclaration(NS ns, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers, PMap<Symbol, Class<?>> imports) {
        this.ns = ns;
        this.aliases = aliases;
        this.refers = refers;
        this.imports = imports;
    }

    @Override
    public String toString() {
        return String.format("(ns %s {alias %s, refer %s, import %s})", ns.name, aliases, refers, imports);
    }
}
