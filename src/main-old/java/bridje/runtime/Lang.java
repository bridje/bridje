package bridje.runtime;

import bridje.reader.Form;

public interface Lang {
    Expr apply(Env env, NS currentNS, Form form);
}
