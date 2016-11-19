package rho.analyser;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.reader.Form;
import rho.runtime.Env;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.Analyser.analyse;
import static rho.analyser.ExprUtil.*;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.SymbolForm.symbolForm;
import static rho.reader.Form.VectorForm.vectorForm;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_ENV;
import static rho.runtime.VarUtil.PLUS_VAR;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SimpleType.INT_TYPE;

public class AnalyserTest {

    @Test
    public void resolvesPlusCall() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));

        assertEquals(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2))), analyse(env, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }

    @Test
    public void resolvesPlusValue() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        assertEquals(globalVarExpr(null, PLUS_VAR), analyse(env, symbolForm("+")));
    }

    @Test
    public void analysesLet() throws Exception {
        Expr expr = analyse(null, listForm(symbolForm("let"), vectorForm(symbolForm("x"), intForm(4), symbolForm("y"), intForm(3)), vectorForm(symbolForm("x"), symbolForm("y"))));

        ValueExpr.VectorExpr body = (ValueExpr.VectorExpr) ((ValueExpr.LetExpr) expr).body;
        LocalVar xLocalVar = ((ValueExpr.LocalVarExpr) body.exprs.get(0)).localVar;
        LocalVar yLocalVar = ((ValueExpr.LocalVarExpr) body.exprs.get(1)).localVar;

        assertEquals(
            letExpr(null,
                vectorOf(
                    letBinding(xLocalVar, intExpr(null, 4)),
                    letBinding(yLocalVar, intExpr(null, 3))),
                vectorExpr(null, vectorOf(localVarExpr(null, xLocalVar), localVarExpr(null, yLocalVar)))),
            expr);
    }

    @Test
    public void analysesIf() throws Exception {
        assertEquals(
            ifExpr(null, boolExpr(null, true), intExpr(null, 1), intExpr(null, 2)),
            analyse(null, listForm(symbolForm("if"), Form.BoolForm.boolForm(true), intForm(1), intForm(2))));
    }

    @Test
    public void analysesLocalCallExpr() throws Exception {
        Expr expr = analyse(PLUS_ENV, listForm(symbolForm("let"), vectorForm(symbolForm("x"), symbolForm("+")), listForm(symbolForm("x"), intForm(1), intForm(2))));

        ValueExpr.CallExpr body = (ValueExpr.CallExpr) ((ValueExpr.LetExpr) expr).body;
        LocalVar xLocalVar = ((ValueExpr.LocalVarExpr) body.exprs.get(0)).localVar;

        assertEquals(
            letExpr(null,
                vectorOf(
                    letBinding(xLocalVar, globalVarExpr(null, PLUS_VAR))),
                callExpr(null, vectorOf(localVarExpr(null, xLocalVar), intExpr(null, 1), intExpr(null, 2)))),
            expr);

    }

    @Test
    public void analysesInlineFn() throws Exception {
        Expr<Form> expr = analyse(PLUS_ENV, listForm(symbolForm("fn"), listForm(symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));
        LocalVar x = ((ValueExpr.FnExpr<Form>) expr).params.get(0);

        assertEquals(
            fnExpr(null, vectorOf(x), varCallExpr(null, PLUS_VAR, vectorOf(localVarExpr(null, x), localVarExpr(null, x)))),
            expr);
    }

    @Test
    public void analysesDefValue() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));

        assertEquals(defExpr(symbol("x"), varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))), analyse(env, listForm(symbolForm("def"), symbolForm("x"), listForm(symbolForm("+"), intForm(1), intForm(2)))));
    }

    @Test
    public void analysesDefFn() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        Expr<Form> expr = analyse(env, listForm(symbolForm("def"), listForm(symbolForm("double"), symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));

        LocalVar x = ((ValueExpr.FnExpr<Form>) ((ActionExpr.DefExpr<Form>) expr).body).params.get(0);

        assertEquals(
            defExpr(symbol("double"), fnExpr(null, vectorOf(x), varCallExpr(null, PLUS_VAR, vectorOf(localVarExpr(null, x), localVarExpr(null, x))))),
            expr);
    }

    @Test
    public void analysesTypeDef() throws Exception {
        assertEquals(new ActionExpr.TypeDefExpr<>(symbol("double"), fnType(vectorOf(INT_TYPE), INT_TYPE)),
            analyse(PLUS_ENV, (listForm(symbolForm("::"), symbolForm("double"), listForm(symbolForm("Fn"), symbolForm("Int"), symbolForm("Int"))))));
    }
}