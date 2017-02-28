package bridje.runtime;

import bridje.reader.Form;

import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.ns;
import static bridje.runtime.Symbol.symbol;

public interface Language {
    FQSymbol CORE_LANGUAGE = fqSym(ns("bridje.core"), symbol("lang"));

    NSEnv loadForm(Env env, NSEnv nsEnv, Form form);
}
