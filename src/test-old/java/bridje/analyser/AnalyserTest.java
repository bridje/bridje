package bridje.analyser;

import bridje.reader.Form;
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
import static org.junit.Assert.assertEquals;

public class AnalyserTest {

    @Test
    public void resolvesPlusCall() throws Exception {
        assertEquals(varCallExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2))), analyse(PLUS_ENV, USER, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }

    @Test
    public void resolvesPlusValue() throws Exception {
        assertEquals(globalVarExpr(PLUS_VAR), analyse(PLUS_ENV, USER, symbolForm("+")));
    }

    @Test
    public void analysesLet() throws Exception {
        Expr expr = analyse(null, USER, listForm(symbolForm("let"), vectorForm(symbolForm("x"), intForm(4), symbolForm("y"), intForm(3)), vectorForm(symbolForm("x"), symbolForm("y"))));

        Expr.VectorExpr body = (Expr.VectorExpr) ((Expr.LetExpr) expr).body;
        LocalVar xLocalVar = ((Expr.LocalVarExpr) body.exprs.get(0)).localVar;
        LocalVar yLocalVar = ((Expr.LocalVarExpr) body.exprs.get(1)).localVar;

        assertEquals(
            letExpr(
                vectorOf(
                    letBinding(xLocalVar, intExpr(4)),
                    letBinding(yLocalVar, intExpr(3))),
                vectorExpr(vectorOf(localVarExpr(xLocalVar), localVarExpr(yLocalVar)))),
            expr);
    }

    @Test
    public void analysesIf() throws Exception {
        assertEquals(
            ifExpr(boolExpr(true), intExpr(1), intExpr(2)),
            analyse(null, USER, listForm(symbolForm("if"), Form.BoolForm.boolForm(true), intForm(1), intForm(2))));
    }

    @Test
    public void analysesLocalCallExpr() throws Exception {
        Expr expr = analyse(PLUS_ENV, USER, listForm(symbolForm("let"), vectorForm(symbolForm("x"), symbolForm("+")), listForm(symbolForm("x"), intForm(1), intForm(2))));

        Expr.CallExpr body = (Expr.CallExpr) ((Expr.LetExpr) expr).body;
        LocalVar xLocalVar = ((Expr.LocalVarExpr) body.exprs.get(0)).localVar;

        assertEquals(
            letExpr(
                vectorOf(
                    letBinding(xLocalVar, globalVarExpr(PLUS_VAR))),
                callExpr(vectorOf(localVarExpr(xLocalVar), intExpr(1), intExpr(2)))),
            expr);

    }

    @Test
    public void analysesInlineFn() throws Exception {
        Expr expr = analyse(PLUS_ENV, USER, listForm(symbolForm("fn"), listForm(symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));
        LocalVar x = ((Expr.FnExpr) expr).params.get(0);

        assertEquals(
            fnExpr(vectorOf(x), varCallExpr(PLUS_VAR, vectorOf(localVarExpr(x), localVarExpr(x)))),
            expr);
    }

    @Test
    public void analysesEmptyNS() throws Exception {
        assertEquals(new Expr.NSExpr(null, ns("my-ns"), Empty.map(), Empty.map(), Empty.map()),
            analyse(PLUS_ENV, USER, listForm(symbolForm("ns"), symbolForm("my-ns"))));
    }

    @Test
    public void analysesNSAliases() throws Exception {
        assertEquals(new Expr.NSExpr(null, ns("my-ns"), HashTreePMap.singleton(symbol("u"), USER), Empty.map(), Empty.map()),
            analyse(PLUS_ENV, USER, listForm(symbolForm("ns"), symbolForm("my-ns"),
                new Form.RecordForm(null, vectorOf(
                    new Form.RecordForm.RecordEntryForm(null,
                        symbol("aliases"),
                        new Form.RecordForm(null, vectorOf(
                            new Form.RecordForm.RecordEntryForm(null, symbol("u"), symbolForm("user"))))))))));
    }

    @Test
    public void analysesNSRefers() throws Exception {
        assertEquals(new Expr.NSExpr(null, ns("my-ns"), Empty.map(), HashTreePMap.singleton(symbol("+"), fqSym(USER, symbol("+"))), Empty.map()),
            analyse(PLUS_ENV, USER, listForm(symbolForm("ns"), symbolForm("my-ns"),
                new Form.RecordForm(null, vectorOf(
                    new Form.RecordForm.RecordEntryForm(null,
                        symbol("refers"),
                        new Form.RecordForm(null, vectorOf(
                            new Form.RecordForm.RecordEntryForm(null, symbol("user"), vectorForm(symbolForm("+")))))))))));
    }

    @Test
    public void analysesDefValue() throws Exception {
        assertEquals(defExpr(symbol("x"), varCallExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2)))), analyse(PLUS_ENV, USER, listForm(symbolForm("def"), symbolForm("x"), listForm(symbolForm("+"), intForm(1), intForm(2)))));
    }

    @Test
    public void analysesDefFn() throws Exception {
        Expr expr = analyse(PLUS_ENV, USER, listForm(symbolForm("def"), listForm(symbolForm("double"), symbolForm("x")), listForm(symbolForm("+"), symbolForm("x"), symbolForm("x"))));

        LocalVar x = ((Expr.FnExpr) ((Expr.DefExpr) expr).body).params.get(0);

        assertEquals(
            defExpr(symbol("double"), fnExpr(vectorOf(x), varCallExpr(PLUS_VAR, vectorOf(localVarExpr(x), localVarExpr(x))))),
            expr);
    }

    @Test
    public void analysesJavaStaticPureFunction() throws Exception {
//        JCall call = new JCall.StaticMethodCall(String.class, "valueOf",
//            new JSignature(
//                vectorOf(new JSignature.JParam(Boolean.TYPE)),
//                new JSignature.JReturn(String.class, Empty.vector())));
//
//        assertEquals(new Expr.DefJExpr(null, fqSym(FOO_NS, symbol("bool->str")), call,
//                new Type.FnType(vectorOf(BOOL_TYPE), STRING_TYPE)),
//            analyse(PLUS_ENV, FOO_NS, (listForm(symbolForm("defj"), symbolForm("bool->str"),
//                symbolForm("String"),
//                symbolForm("valueOf"),
//                listForm(symbolForm("Fn"), symbolForm("Bool"), symbolForm("Str"))))));
        throw new UnsupportedOperationException();
    }

    @Test
    public void analysesSimpleDefData() throws Exception {
//        assertEquals(
//            new Expr.DefDataExpr(null, new DataType(fqSym(USER, symbol("Month")),
//                Empty.vector(), vectorOf(
//                new ValueConstructor(fqSym(USER, symbol("Jan"))),
//                new ValueConstructor(fqSym(USER, symbol("Feb"))),
//                new ValueConstructor(fqSym(USER, symbol("Mar")))))),
//            analyse(PLUS_ENV,
//                USER, listForm(symbolForm("defdata"), symbolForm("Month"),
//                    symbolForm("Jan"),
//                    symbolForm("Feb"),
//                    symbolForm("Mar"))));
        throw new UnsupportedOperationException();
    }

    @Test
    public void analysesRecursiveDataType() throws Exception {
//        assertEquals(new Expr.DefDataExpr(null, new DataType(
//                fqSym(USER, symbol("IntList")),
//                Empty.vector(),
//                vectorOf(
//                    new VectorConstructor(fqSym(USER, symbol("IntListCons")), paramNames),
//                    new ValueConstructor(fqSym(USER, symbol("EmptyIntList")))))),
//            analyse(PLUS_ENV,
//                USER, listForm(symbolForm("defdata"), symbolForm("IntList"),
//                    listForm(symbolForm("IntListCons"), symbolForm("Int"), symbolForm("IntList")),
//                    symbolForm("EmptyIntList"))));
        throw new UnsupportedOperationException();
    }

    @Test
    public void analysesParameterisedDataType() throws Exception {
//        Type.TypeVar a = new Type.TypeVar();
//        assertEquals(
//            new Expr.DefDataExpr(null, new DataType<>(fqSym(USER, symbol("Foo")),
//                vectorOf(a),
//                vectorOf(new VectorConstructor(fqSym(USER, symbol("Foo")), paramNames)))),
//
//            analyse(PLUS_ENV,
//                USER, listForm(symbolForm("defdata"), listForm(symbolForm("Foo"), symbolForm("a")),
//                    listForm(symbolForm("Foo"), symbolForm("a")))));
        throw new UnsupportedOperationException();
    }

    @Test
    public void analysesQualifiedPlusCall() throws Exception {
        assertEquals(varCallExpr(PLUS_VAR, vectorOf(intExpr(4), intExpr(3))), analyse(PLUS_ENV, FOO_NS, (listForm(qSymbolForm("u", "+"), intForm(4), intForm(3)))));
    }
}