package rho.compiler;

import org.junit.Test;

import static rho.Util.uniqueInt;

public class ClassDefinerTest {

    @Test
    public void makesClass() throws Exception {
        ClassDefiner.defineClass(NewClass.newClass("user.ClassDefinerTest$$" + uniqueInt()));
    }
}