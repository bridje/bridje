package bridje.kernel;

import bridje.reader.Form;
import bridje.runtime.Eval;
import bridje.runtime.EvalResult;
import bridje.runtime.NS;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelloWorldTest {

    public static final NS HELLO_WORLD_NS = NS.ns("bridje.kernel.hello-world");

    @Test
    public void loadsHelloWorldNS() throws Exception {
        EvalResult<?> evalResult = Eval.loadNS(HELLO_WORLD_NS);
    }

    @Test
    public void evalsHelloWorld() throws Exception {
        EvalResult<?> evalResult = Eval.loadNS(HELLO_WORLD_NS);
        assertEquals(Eval.eval(evalResult.env, HELLO_WORLD_NS, Form.SymbolForm.symbolForm("hello")).value, "Hello world!");
    }
}
