package bridje;

import bridje.reader.Form;
import bridje.runtime.Eval;
import bridje.runtime.EvalResult;
import bridje.runtime.NS;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelloWorldTest {
    @Test
    public void evalsHelloWorld() throws Exception {
        NS helloWorldNS = NS.ns("bridje.hello-world");
        EvalResult<?> evalResult = Eval.loadNS(helloWorldNS);
        assertEquals(Eval.eval(evalResult.env, helloWorldNS, Form.SymbolForm.symbolForm("hello")).value, "Hello world!");
    }
}
