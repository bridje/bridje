package rho.compiler;

import org.junit.Test;
import rho.runtime.Env;

public class ClassDefinerTest {

    @Test
    public void makesClass() throws Exception {
        ClassDefiner.defineClass(Env.env(), NewClass.newClass("user.ClassDefinerTest"));
    }
}