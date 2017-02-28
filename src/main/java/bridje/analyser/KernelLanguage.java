package bridje.analyser;

import bridje.reader.Form;
import bridje.runtime.Env;
import bridje.runtime.Language;
import bridje.runtime.NSEnv;

public class KernelLanguage implements Language {

    @Override
    public NSEnv loadForm(Env env, NSEnv nsEnv, Form form) {
        throw new UnsupportedOperationException();
    }
}
