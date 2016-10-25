package rho.analyser;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.runtime.Env;
import rho.runtime.Var;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.Analyser.analyse;
import static rho.analyser.ValueExpr.CallExpr.callExpr;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.SymbolForm.symbolForm;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.Var.var;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SimpleType.INT_TYPE;

public class AnalyserTest {

    @Test
    public void resolvesPlus() throws Exception {
        Var var = var(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE));

        Env env = new Env(HashTreePMap.singleton(symbol("+"), var));

        assertEquals(callExpr(var, vectorOf(intExpr(1), intExpr(2))), analyse(env, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }
}