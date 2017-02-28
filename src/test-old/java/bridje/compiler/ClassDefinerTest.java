package bridje.compiler;

import org.junit.Test;

import static bridje.Util.uniqueInt;

public class ClassDefinerTest {

    @Test
    public void makesClass() throws Exception {
        ClassDefiner.defineClass(NewClass.newClass("user.ClassDefinerTest$$" + uniqueInt()));
    }
}