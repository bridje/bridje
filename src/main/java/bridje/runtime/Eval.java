package bridje.runtime;

import bridje.compiler.EnvUpdate;
import bridje.reader.Form;

public class Eval {

    static Object eval(Env env, NS ns, Form form) {
        throw new UnsupportedOperationException();
    }

    public static Object eval(NS ns, Form form) {
        Env startEnv = Env.env();
        Object result = eval(startEnv, ns, form);

        if (result instanceof EnvUpdate) {
            return Env.eval(currentEnv -> {
               if (currentEnv == startEnv) {
                   // shortcircuit
                   return ((EnvUpdate) result).updateEnv(currentEnv);
               } else {
                   return ((EnvUpdate) eval(currentEnv, ns, form)).updateEnv(currentEnv);
               }
            });
        } else {
            return result;
        }
    }
}
