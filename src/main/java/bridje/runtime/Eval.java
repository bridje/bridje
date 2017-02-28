package bridje.runtime;

import bridje.analyser.Expr;
import bridje.reader.Form;
import bridje.reader.FormReader;
import bridje.util.Pair;
import org.pcollections.PVector;

import java.io.IOException;
import java.net.URL;

import static bridje.Panic.panic;
import static bridje.analyser.Analyser.parseNSDeclaration;

public class Eval {

    public static Pair<NSEnv, PVector<Expr>> analyseNS(Env env, NS ns, PVector<Form> forms) {
        System.out.println(parseNSDeclaration(forms.get(0)));
        throw new UnsupportedOperationException();
    }

    public static EvalResult<?> loadNS(Env env, NS ns, PVector<Form> forms) {
        Pair<NSEnv, PVector<Expr>> analyseResult = analyseNS(env, ns, forms);

        return new EvalResult<>(env, null);
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
