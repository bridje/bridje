package bridje.analyser;

import bridje.reader.Form;
import bridje.runtime.Env;
import bridje.runtime.FQSymbol;
import bridje.runtime.NSEnv;
import bridje.util.Pair;

import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.ns;
import static bridje.runtime.Symbol.symbol;

public interface Language {
    FQSymbol CORE_LANGUAGE = fqSym(ns("bridje.core"), symbol("lang"));

    Pair<Expr, NSAnalysisEnv> loadForm(Env env, NSAnalysisEnv nsAnalysisEnv, Form form);
}
