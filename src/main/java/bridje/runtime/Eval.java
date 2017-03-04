package bridje.runtime;

import bridje.analyser.Expr;
import bridje.compiler.Compiler;
import bridje.reader.Form;
import bridje.reader.FormReader;
import org.pcollections.PVector;

import java.io.IOException;
import java.net.URL;

import static bridje.Panic.panic;
import static bridje.analyser.Analyser.analyse;
import static bridje.analyser.Analyser.parseNSDeclaration;

public class Eval {

    public static EvalResult<?> loadNS(Env env, NS ns, PVector<Form> forms) {
        if (forms.isEmpty()) {
            throw panic("Empty NS: '%s'", ns.name);
        }

        env = env.withNSEnv(ns, parseNSDeclaration(forms.get(0), ns));
        Compiler compiler = new Compiler(ns);

        boolean isKernel = NS.KERNEL.name.equals(ns.name) || ns.name.startsWith(NS.KERNEL.name + ".");

        for (Form form : forms.minus(0)) {
            Expr expr;
            if (isKernel)
                expr = analyse(env, ns, form);
            else
                throw new UnsupportedOperationException();

            env = compiler.compile(env, expr).env;
        }

        compiler.build();

        return new EvalResult<>(env, ns);
    }

    public static EvalResult<?> loadNS(NS ns, PVector<Form> forms) {
        return Env.eval(env -> loadNS(env, ns, forms));
    }

    private static URL nsURL(NS ns) {
        String nsPath = ns.name.replace('.', '/');

        URL url = Eval.class.getClassLoader().getResource(nsPath + ".brj");
        if (url != null) {
            return url;
        }

        throw panic("Can't find file for namespace '%s'", ns);
    }

    private static PVector<Form> readForms(NS ns, URL url) {
        try {
            return FormReader.readAll(url);
        } catch (IOException e) {
            throw panic(e, "Error reading namespace '%s' from '%s'", ns, url);
        }
    }

    public static EvalResult<?> loadNS(Env env, NS ns) {
        return loadNS(env, ns, readForms(ns, nsURL(ns)));
    }

    public static EvalResult<?> loadNS(NS ns) {
        return Env.eval(env -> loadNS(env, ns));
    }

    public static EvalResult eval(Env env, NS currentNS, Form form) {
        throw new UnsupportedOperationException();
    }
}
