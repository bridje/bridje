package rho.analyser;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.runtime.Env;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.Analyser.analyse;
import static rho.analyser.ValueExpr.CallExpr.callExpr;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.SymbolForm.symbolForm;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_VAR;

public class AnalyserTest {

    @Test
    public void resolvesPlus() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));

        assertEquals(callExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2))), analyse(env, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }
}