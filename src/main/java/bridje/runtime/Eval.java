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
    public static void loadNS(NS ns, PVector<Form> forms, Lang lang) {
        System.out.println(forms);
        throw new UnsupportedOperationException();
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

    public static void loadNS(NS ns) {
        Pair<URL, Lang> foundNS = nsURL(ns);

        loadNS(ns, readForms(ns, foundNS.left), foundNS.right);
    }

    public static EvalResult eval(NS currentNS, Form form) {
        throw new UnsupportedOperationException();
    }
}
