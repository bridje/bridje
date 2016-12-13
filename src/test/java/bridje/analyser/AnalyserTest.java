package bridje.analyser;

import bridje.reader.Form;
import bridje.runtime.DataType;
import bridje.runtime.DataTypeConstructor.ValueConstructor;
import bridje.runtime.DataTypeConstructor.VectorConstructor;
import bridje.types.Type;
import org.junit.Test;
import org.pcollections.Empty;
import org.pcollections.HashTreePMap;

import static bridje.Util.vectorOf;
import static bridje.analyser.Analyser.analyse;
import static bridje.analyser.ExprUtil.*;
import static bridje.reader.Form.IntForm.intForm;
import static bridje.reader.Form.ListForm.listForm;
import static bridje.reader.Form.QSymbolForm.qSymbolForm;
import static bridje.reader.Form.SymbolForm.symbolForm;
import static bridje.reader.Form.VectorForm.vectorForm;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.USER;
import static bridje.runtime.NS.ns;
import static bridje.runtime.Symbol.symbol;
import static bridje.runtime.VarUtil.*;
import static bridje.types.Type.FnType.fnType;
import static bridje.types.Type.SimpleType.INT_TYPE;
import static org.junit.Assert.assertEquals;

public class AnalyserTest {

    @Test
    public void resolvesPlusCall() throws Exception {
        assertEquals(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2))), analyse(PLUS_ENV, USER, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }

    @Test
    public void resolvesPlusValue() throws Exception {
        assertEquals(globalVarExpr(null, PLUS_VAR), analyse(PLUS_ENV, USER, symbolForm("+")));
    }

    @Test
    public void analysesLet() throws Exception {
        Expr expr = analyse(null, USER, listForm(symbolForm("let"), vectorForm(symbolForm("x"), intForm(4), symbolForm("y"), intForm(3)), vectorForm(symbolForm("x"), symbolForm("y"))));

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
            analyse(null, USER, listForm(symbolForm("if"), Form.BoolForm.boolForm(true), intForm(1), intForm(2))));
    }

    @Test
    public void analysesLocalCallExpr() throws Exception {
        Expr expr = analyse(PLUS_ENV, USER, listForm(symbolForm("let"), vectorForm(symbolForm("x"), symbolForm("+")), listForm(symbolForm("x"), intForm(1), intForm(2))));

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
        Expr<Void> expr = analyse(PLUS_ENV, USER, listForm(symbolForm("fn"), listForm(symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));
        LocalVar x = ((Expr.FnExpr<Void>) expr).params.get(0);

        assertEquals(
            fnExpr(null, vectorOf(x), varCallExpr(null, PLUS_VAR, vectorOf(localVarExpr(null, x), localVarExpr(null, x)))),
            expr);
    }

    @Test
    public void analysesEmptyNS() throws Exception {
        assertEquals(new Expr.NSExpr<Void>(null, null, ns("my-ns"), Empty.map(), Empty.map(), Empty.map()),
            analyse(PLUS_ENV, USER, listForm(symbolForm("ns"), symbolForm("my-ns"))));
    }

    @Test
    public void analysesNSAliases() throws Exception {
        assertEquals(new Expr.NSExpr<Void>(null, null, ns("my-ns"), HashTreePMap.singleton(symbol("u"), USER), Empty.map(), Empty.map()),
            analyse(PLUS_ENV, USER, listForm(symbolForm("ns"), symbolForm("my-ns"),
                new Form.RecordForm(null, vectorOf(
                    new Form.RecordForm.RecordEntryForm(null,
                        symbol("aliases"),
                        new Form.RecordForm(null, vectorOf(
                            new Form.RecordForm.RecordEntryForm(null, symbol("u"), symbolForm("user"))))))))));
    }

    @Test
    public void analysesNSRefers() throws Exception {
        assertEquals(new Expr.NSExpr<Void>(null, null, ns("my-ns"), Empty.map(), HashTreePMap.singleton(symbol("+"), fqSym(USER, symbol("+"))), Empty.map()),
            analyse(PLUS_ENV, USER, listForm(symbolForm("ns"), symbolForm("my-ns"),
                new Form.RecordForm(null, vectorOf(
                    new Form.RecordForm.RecordEntryForm(null,
                        symbol("refers"),
                        new Form.RecordForm(null, vectorOf(
                            new Form.RecordForm.RecordEntryForm(null, symbol("user"), vectorForm(symbolForm("+")))))))))));
    }

    @Test
    public void analysesDefValue() throws Exception {
        assertEquals(defExpr(null, symbol("x"), varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))), analyse(PLUS_ENV, USER, listForm(symbolForm("def"), symbolForm("x"), listForm(symbolForm("+"), intForm(1), intForm(2)))));
    }

    @Test
    public void analysesDefFn() throws Exception {
        Expr<Void> expr = analyse(PLUS_ENV, USER, listForm(symbolForm("def"), listForm(symbolForm("double"), symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));

        LocalVar x = ((Expr.FnExpr<Void>) ((Expr.DefExpr<Void>) expr).body).params.get(0);

        assertEquals(
            defExpr(null, symbol("double"), fnExpr(null, vectorOf(x), varCallExpr(null, PLUS_VAR, vectorOf(localVarExpr(null, x), localVarExpr(null, x))))),
            expr);
    }

    @Test
    public void analysesTypeDef() throws Exception {
        assertEquals(new Expr.TypeDefExpr<>(null, null, fqSym(USER, symbol("double")), fnType(vectorOf(INT_TYPE), INT_TYPE)),
            analyse(PLUS_ENV, USER, (listForm(symbolForm("::"), symbolForm("double"), listForm(symbolForm("Fn"), symbolForm("Int"), symbolForm("Int"))))));
    }

    @Test
    public void analysesSimpleDefData() throws Exception {
        assertEquals(
            new Expr.DefDataExpr<>(null, null, new DataType<>(null, fqSym(USER, symbol("Month")),
                Empty.vector(), vectorOf(
                new ValueConstructor<>(null, fqSym(USER, symbol("Jan"))),
                new ValueConstructor<>(null, fqSym(USER, symbol("Feb"))),
                new ValueConstructor<>(null, fqSym(USER, symbol("Mar")))))),
            analyse(PLUS_ENV,
                USER, listForm(symbolForm("defdata"), symbolForm("Month"),
                    symbolForm("Jan"),
                    symbolForm("Feb"),
                    symbolForm("Mar"))));
    }

    @Test
    public void analysesRecursiveDataType() throws Exception {
        assertEquals(new Expr.DefDataExpr<>(null, null, new DataType<>(null,
                fqSym(USER, symbol("IntList")),
                Empty.vector(),
                vectorOf(
                    new VectorConstructor<>(null, fqSym(USER, symbol("IntListCons")), vectorOf(INT_TYPE, new Type.DataTypeType(fqSym(USER, symbol("IntList")), null))),
                    new ValueConstructor<>(null, fqSym(USER, symbol("EmptyIntList")))))),
            analyse(PLUS_ENV,
                USER, listForm(symbolForm("defdata"), symbolForm("IntList"),
                    listForm(symbolForm("IntListCons"), symbolForm("Int"), symbolForm("IntList")),
                    symbolForm("EmptyIntList"))));
    }

    @Test
    public void analysesParameterisedDataType() throws Exception {
        Type.TypeVar a = new Type.TypeVar();
        assertEquals(
            new Expr.DefDataExpr<Void>(null, null, new DataType<>(null, fqSym(USER, symbol("Foo")),
                vectorOf(a),
                vectorOf(new VectorConstructor<>(null, fqSym(USER, symbol("Foo")), vectorOf(a))))),

            analyse(PLUS_ENV,
                USER, listForm(symbolForm("defdata"), listForm(symbolForm("Foo"), symbolForm("a")),
                    listForm(symbolForm("Foo"), symbolForm("a")))));
    }

    @Test
    public void analysesQualifiedPlusCall() throws Exception {
        assertEquals(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 4), intExpr(null, 3))), analyse(PLUS_ENV, FOO_NS, (listForm(qSymbolForm("u", "+"), intForm(4), intForm(3)))));
    }
}