package rho.analyser;

import org.junit.Test;
import rho.reader.Form;
import rho.runtime.DataType;
import rho.runtime.DataTypeConstructor.ValueConstructor;
import rho.runtime.DataTypeConstructor.VectorConstructor;
import rho.types.Type;

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
        assertEquals(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2))), analyse(PLUS_ENV, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }

    @Test
    public void resolvesPlusValue() throws Exception {
        assertEquals(globalVarExpr(null, PLUS_VAR), analyse(PLUS_ENV, symbolForm("+")));
    }

    @Test
    public void analysesLet() throws Exception {
        Expr expr = analyse(null, listForm(symbolForm("let"), vectorForm(symbolForm("x"), intForm(4), symbolForm("y"), intForm(3)), vectorForm(symbolForm("x"), symbolForm("y"))));

        Expr.VectorExpr body = (Expr.VectorExpr) ((Expr.LetExpr) expr).body;
        LocalVar xLocalVar = ((Expr.LocalVarExpr) body.exprs.get(0)).localVar;
        LocalVar yLocalVar = ((Expr.LocalVarExpr) body.exprs.get(1)).localVar;

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

        Expr.CallExpr body = (Expr.CallExpr) ((Expr.LetExpr) expr).body;
        LocalVar xLocalVar = ((Expr.LocalVarExpr) body.exprs.get(0)).localVar;

        assertEquals(
            letExpr(null,
                vectorOf(
                    letBinding(xLocalVar, globalVarExpr(null, PLUS_VAR))),
                callExpr(null, vectorOf(localVarExpr(null, xLocalVar), intExpr(null, 1), intExpr(null, 2)))),
            expr);

    }

    @Test
    public void analysesInlineFn() throws Exception {
        Expr<Void> expr = analyse(PLUS_ENV, listForm(symbolForm("fn"), listForm(symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));
        LocalVar x = ((Expr.FnExpr<Void>) expr).params.get(0);

        assertEquals(
            fnExpr(null, vectorOf(x), varCallExpr(null, PLUS_VAR, vectorOf(localVarExpr(null, x), localVarExpr(null, x)))),
            expr);
    }

    @Test
    public void analysesDefValue() throws Exception {
        assertEquals(defExpr(null, symbol("x"), varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))), analyse(PLUS_ENV, listForm(symbolForm("def"), symbolForm("x"), listForm(symbolForm("+"), intForm(1), intForm(2)))));
    }

    @Test
    public void analysesDefFn() throws Exception {
        Expr<Void> expr = analyse(PLUS_ENV, listForm(symbolForm("def"), listForm(symbolForm("double"), symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));

        LocalVar x = ((Expr.FnExpr<Void>) ((Expr.DefExpr<Void>) expr).body).params.get(0);

        assertEquals(
            defExpr(null, symbol("double"), fnExpr(null, vectorOf(x), varCallExpr(null, PLUS_VAR, vectorOf(localVarExpr(null, x), localVarExpr(null, x))))),
            expr);
    }

    @Test
    public void analysesTypeDef() throws Exception {
        assertEquals(new Expr.TypeDefExpr<>(null, null, symbol("double"), fnType(vectorOf(INT_TYPE), INT_TYPE)),
            analyse(PLUS_ENV, (listForm(symbolForm("::"), symbolForm("double"), listForm(symbolForm("Fn"), symbolForm("Int"), symbolForm("Int"))))));
    }

    @Test
    public void analysesSimpleDefData() throws Exception {
        assertEquals(
            new Expr.DefDataExpr<>(null, null, new DataType<>(null, symbol("Month"),
                vectorOf(
                    new ValueConstructor<>(null, symbol("Jan")),
                    new ValueConstructor<>(null, symbol("Feb")),
                    new ValueConstructor<>(null, symbol("Mar"))))),
            analyse(PLUS_ENV,
                listForm(symbolForm("defdata"), symbolForm("Month"),
                    symbolForm("Jan"),
                    symbolForm("Feb"),
                    symbolForm("Mar"))));
    }

    @Test
    public void analysesRecursiveDataType() throws Exception {
        assertEquals(new Expr.DefDataExpr<>(null, null, new DataType<>(null, symbol("IntList"),
                vectorOf(
                    new VectorConstructor<>(null, symbol("IntListCons"), vectorOf(INT_TYPE, new Type.DataTypeType(symbol("IntList"), null))),
                    new ValueConstructor<>(null, symbol("EmptyIntList"))))),
            analyse(PLUS_ENV,
                listForm(symbolForm("defdata"), symbolForm("IntList"),
                    listForm(symbolForm("IntListCons"), symbolForm("Int"), symbolForm("IntList")),
                    symbolForm("EmptyIntList"))));
    }

    @Test
    public void analysesParameterisedDataType() throws Exception {
        Type.TypeVar a = new Type.TypeVar();
        assertEquals(
            // TODO need to pass the typeVar to `new DataType`
            new Expr.DefDataExpr<Void>(null, null, new DataType<>(null, symbol("Foo"),
                vectorOf(new VectorConstructor<>(null, symbol("Foo"), vectorOf(a))))),

            analyse(PLUS_ENV,
                listForm(symbolForm("defdata"), listForm(symbolForm("Foo"), symbolForm("a")),
                    listForm(symbolForm("Foo"), symbolForm("a")))));
    }
}