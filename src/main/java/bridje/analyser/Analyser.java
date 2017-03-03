package bridje.analyser;

import bridje.reader.Form;
import bridje.reader.FormVisitor;
import bridje.runtime.Env;
import bridje.runtime.NS;
import bridje.util.Pair;
import org.pcollections.Empty;
import org.pcollections.PVector;

import static bridje.Panic.panic;
import static bridje.analyser.ListParser.*;
import static bridje.runtime.Symbol.symbol;
import static bridje.util.Pair.pair;

public class Analyser {

    public static NSDeclaration parseNSDeclaration(Form form) {
        return parseForm(form, LIST_PARSER.bind(listForm ->
            nestedListParser(listForm.forms,
                SYMBOL_PARSER.bind(nsSymForm -> {
                    if (!nsSymForm.sym.equals(symbol("ns"))) {
                        return fail(new ParseException.UnexpectedFormException("ns", nsSymForm.sym));
                    } else {
                        return SYMBOL_PARSER.bind(nsNameForm -> {
                            NS ns = NS.ns(nsNameForm.sym.sym);

                            return anyOf(
                                parseEnd(new NSDeclaration(ns)),
                                RECORD_PARSER.bind(nsOptsRecordForm ->
                                    // TODO parse this
                                    parseEnd(new NSDeclaration(ns))));
                        });
                    }
                }),
                ListParser::pure)))
            .orThrow().left;
    }

    public static Expr loadKernelForm(Env env, NSDeclaration nsDeclaration, Form form) {
        return form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr visit(Form.BoolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.StringForm form) {
                return new Expr.StringExpr(form.range, form.string);
            }

            @Override
            public Expr visit(Form.IntForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.VectorForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.SetForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.RecordForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.ListForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.SymbolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Pair<NSDeclaration, PVector<Expr>> analyseNS(Env env, NS ns, PVector<Form> forms) {
        if (forms.isEmpty()) {
            throw panic("No forms in NS '%s'", ns.name);
        }

        NSDeclaration nsDeclaration = parseNSDeclaration(forms.get(0));
        // TODO figure out what order we're compiling the NS in
        return pair(nsDeclaration, Empty.vector());
    }
}
