package rho.analyser;

import org.pcollections.PVector;
import rho.reader.Form;
import rho.reader.FormVisitor;
import rho.runtime.Env;
import rho.runtime.Symbol;
import rho.runtime.Var;

import java.util.Optional;

import static rho.Util.toPVector;

public class Analyser {

    public static ValueExpr analyseValueExpr(Env env, Form form) {
        return form.accept(new FormVisitor<ValueExpr>() {
            @Override
            public ValueExpr visit(Form.BoolForm form) {
                return ValueExpr.BoolExpr.fromForm(form);
            }

            @Override
            public ValueExpr visit(Form.StringForm form) {
                return ValueExpr.StringExpr.fromForm(form);
            }

            @Override
            public ValueExpr visit(Form.IntForm form) {
                return ValueExpr.IntExpr.fromForm(form);
            }

            @Override
            public ValueExpr visit(Form.VectorForm form) {
                return ValueExpr.VectorExpr.vectorExpr(form.range, form.forms.stream().map(f -> analyseValueExpr(env, f)).collect(toPVector()));
            }

            @Override
            public ValueExpr visit(Form.SetForm form) {
                return ValueExpr.SetExpr.setExpr(form.range, form.forms.stream().map(f -> analyseValueExpr(env, f)).collect(toPVector()));
            }

            @Override
            public ValueExpr visit(Form.ListForm form) {
                PVector<Form> forms = form.forms;

                if (!forms.isEmpty()) {
                    Form firstForm = forms.get(0);

                    if (firstForm instanceof Form.SymbolForm) {
                        Symbol sym = ((Form.SymbolForm) firstForm).sym;
                        Var var =
                            Optional.ofNullable(env.vars.get(sym))
                                .orElseThrow(UnsupportedOperationException::new);

                        return new ValueExpr.CallExpr(form.range, var,
                            forms.subList(1, forms.size()).stream().map(f -> analyseValueExpr(env, f)).collect(toPVector()));
                    }
                }

                throw new UnsupportedOperationException();


            }

            @Override
            public ValueExpr visit(Form.SymbolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr analyse(Env env, Form form) {
        return form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr visit(Form.BoolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.StringForm form) {
                return analyseValueExpr(env, form);
            }

            @Override
            public Expr visit(Form.IntForm form) {
                return analyseValueExpr(env, form);
            }

            @Override
            public Expr visit(Form.VectorForm form) {
                return analyseValueExpr(env, form);
            }

            @Override
            public Expr visit(Form.SetForm form) {
                return analyseValueExpr(env, form);
            }

            @Override
            public Expr visit(Form.ListForm form) {
                // TODO this isn't always going to be a ValueExpr
                return analyseValueExpr(env, form);
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
}
