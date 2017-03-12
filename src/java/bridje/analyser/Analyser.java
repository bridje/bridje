package bridje.analyser;

import bridje.reader.Form;
import bridje.reader.FormVisitor;
import bridje.runtime.Env;
import bridje.runtime.NS;
import bridje.runtime.NSEnv;
import bridje.runtime.Symbol;
import org.pcollections.Empty;

import static bridje.Util.fmap;
import static bridje.analyser.ListParser.*;
import static bridje.analyser.ParseException.MismatchingNSException.mismatchedNS;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.Symbol.symbol;

public class Analyser {

    public static NSEnv parseNSDeclaration(Form form, NS ns) {
        return parseForm(form, LIST_PARSER.bind(listForm ->
            nestedListParser(listForm.forms,
                SYMBOL_PARSER.bind(nsSymForm -> {
                    if (!nsSymForm.sym.equals(symbol("ns"))) {
                        return fail(new ParseException.UnexpectedFormException("ns", nsSymForm.sym));
                    } else {
                        return SYMBOL_PARSER.bind(nsNameForm -> {
                            if (!ns.name.equals(nsNameForm.sym.sym)) {
                                return fail(mismatchedNS(ns, nsNameForm));
                            } else {
                                return anyOf(
                                    parseEnd(NSEnv.EMPTY),
                                    RECORD_PARSER.bind(nsOptsRecordForm ->
                                        // TODO parse this
                                        parseEnd(NSEnv.EMPTY)));
                            }
                        });
                    }
                }),
                ListParser::pure)));
    }

    private final Env env;
    private final NS currentNS;

    private Analyser(Env env, NS currentNS) {
        this.env = env;
        this.currentNS = currentNS;
    }

    private ValueExpr analyseValueExpr(Form form) {

        return form.accept(new FormVisitor<ValueExpr>() {
            @Override
            public ValueExpr visit(Form.BoolForm form) {
                return new ValueExpr.BoolExpr(form.range, form.value);
            }

            @Override
            public ValueExpr visit(Form.StringForm form) {
                return new ValueExpr.StringExpr(form.range, form.string);
            }

            @Override
            public ValueExpr visit(Form.IntForm form) {
                return new ValueExpr.IntExpr(form.range, form.num);
            }

            @Override
            public ValueExpr visit(Form.VectorForm form) {
                return new ValueExpr.VectorExpr(form.range, fmap(form.forms, Analyser.this::analyseValueExpr));
            }

            @Override
            public ValueExpr visit(Form.SetForm form) {
                return new ValueExpr.SetExpr(form.range, fmap(form.forms, Analyser.this::analyseValueExpr));
            }

            @Override
            public ValueExpr visit(Form.RecordForm form) {
                return null;
            }

            @Override
            public ValueExpr visit(Form.ListForm form) {
                return null;
            }

            @Override
            public ValueExpr visit(Form.SymbolForm form) {
                return null;
            }
        });
    }

    private final ListParser<ValueExpr> valueExprParser = oneOf(f -> ParseResult.success(analyseValueExpr(f)));

    private ListParser<ValueExpr> ifParser(Form form) {
        return valueExprParser.bind(testExpr ->
            valueExprParser.bind(thenExpr ->
                valueExprParser.bind(elseExpr ->
                    parseEnd(new ValueExpr.IfExpr(form.range, testExpr, thenExpr, elseExpr)))));
    }

    private Expr analyse0(Form form) {

        return form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr visit(Form.BoolForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.StringForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.IntForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.VectorForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.SetForm form) {
                return analyseValueExpr(form);
            }

            @Override
            public Expr visit(Form.RecordForm form) {
                throw new UnsupportedOperationException();
            }

            private ListParser<Expr> defParser() {
                return SYMBOL_PARSER.bind(symForm ->
                    valueExprParser.bind(bodyExpr ->
                        parseEnd((Expr) new ActionExpr.DefExpr(form.range, fqSym(currentNS, symForm.sym), Empty.vector(), bodyExpr))));
            }

            @Override
            public Expr visit(Form.ListForm form) {
                return parseForms(form.forms,
                    SYMBOL_PARSER.bind(firstSymForm -> {
                        Symbol firstSym = firstSymForm.sym;
                        if (firstSym.ns == null) {
                            switch (firstSym.sym) {
                                case "if":
                                    return ifParser(form).fmap(e -> e);
                                case "def":
                                    return defParser();
                            }
                        }

                        throw new UnsupportedOperationException();
                    }));

            }

            @Override
            public Expr visit(Form.SymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr analyse(Env env, NS currentNS, Form form) {
        return new Analyser(env, currentNS).analyse0(form);
    }
}
