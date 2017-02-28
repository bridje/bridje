package bridje.runtime;

import bridje.reader.Form;
import bridje.reader.FormReader;
import bridje.util.Pair;
import org.pcollections.PVector;

import java.io.IOException;
import java.net.URL;

import static bridje.Panic.panic;
import static bridje.runtime.Lang.CORE;
import static bridje.runtime.Lang.KERNEL;
import static bridje.util.Pair.pair;

public class Eval {
    public static EvalResult<?> loadNS(Env env, NS ns, PVector<Form> forms, Lang lang) {
        System.out.println(env);
        System.out.println(forms);
        System.out.println(lang);
        return new EvalResult<>(env, null);
    }

    public static EvalResult<?> loadNS(NS ns, PVector<Form> forms, Lang lang) {
        return Env.eval(env -> loadNS(env, ns, forms, lang));
    }

    private static Pair<URL, Lang> nsURL(NS ns) {
        String nsPath = ns.name.replace('.', '/');

        URL kernelURL = Eval.class.getClassLoader().getResource(nsPath + ".brjk");
        if (kernelURL != null) {
            return pair(kernelURL, KERNEL);
        }

        URL coreURL = Eval.class.getClassLoader().getResource(nsPath + ".brj");
        if (coreURL != null) {
            return pair(coreURL, CORE);
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
        Pair<URL, Lang> foundNS = nsURL(ns);

        return loadNS(env, ns, readForms(ns, foundNS.left), foundNS.right);
    }

    public static EvalResult<?> loadNS(NS ns) {
        return Env.eval(env -> loadNS(env, ns));
    }

    public static EvalResult eval(Env env, NS currentNS, Form form) {
        throw new UnsupportedOperationException();
    }
}
