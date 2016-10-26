package rho.analyser;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.analyser.LocalEnv.LocalVar;
import rho.reader.Form;
import rho.runtime.Env;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.Analyser.analyse;
import static rho.analyser.ValueExpr.CallExpr.callExpr;
import static rho.analyser.ValueExpr.IfExpr.ifExpr;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.LetExpr.LetBinding.letBinding;
import static rho.analyser.ValueExpr.LetExpr.letExpr;
import static rho.analyser.ValueExpr.LocalVarExpr.localVarExpr;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.SymbolForm.symbolForm;
import static rho.reader.Form.VectorForm.vectorForm;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_VAR;

public class AnalyserTest {

    @Test
    public void resolvesPlus() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));

        assertEquals(callExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2))), analyse(env, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }

    @Test
    public void analysesLet() throws Exception {
        Expr expr = analyse(null, listForm(symbolForm("let"), vectorForm(symbolForm("x"), intForm(4)), symbolForm("x")));
        LocalVar localVar = ((ValueExpr.LocalVarExpr) ((ValueExpr.LetExpr) expr).body).localVar;
        assertEquals(
            letExpr(vectorOf(letBinding(symbol("x"), intExpr(4))), localVarExpr(localVar)),
            expr);
    }

    @Test
    public void analysesIf() throws Exception {
        assertEquals(
            ifExpr(ValueExpr.BoolExpr.boolExpr(true), intExpr(1), intExpr(2)),
            analyse(null, listForm(symbolForm("if"), Form.BoolForm.boolForm(true), intForm(1), intForm(2))));
    }
}