package bridje;

import bridje.reader.Form;
import bridje.runtime.Eval;
import bridje.runtime.NS;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelloWorldTest {
    @Test
    public void evalsHelloWorld() throws Exception {
        NS helloWorldNS = NS.ns("bridje.hello-world");
        Eval.requireNS(helloWorldNS);
        assertEquals(Eval.eval(helloWorldNS, Form.SymbolForm.symbolForm("hello")).value, "Hello world!");
    }
}
