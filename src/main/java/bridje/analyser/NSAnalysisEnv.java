package bridje.analyser;

import bridje.runtime.Symbol;
import org.pcollections.PMap;

public class NSAnalysisEnv {
    public final PMap<Symbol, PMap<Symbol, Object>> syms;

    public NSAnalysisEnv(PMap<Symbol, PMap<Symbol, Object>> syms) {
        this.syms = syms;
    }
}
