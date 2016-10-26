package rho.analyser;

import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.analyser.ValueExpr.LocalVarExpr;
import rho.reader.Form;
import rho.reader.FormVisitor;
import rho.runtime.Env;
import rho.runtime.Symbol;
import rho.runtime.Var;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static rho.Util.toPVector;
import static rho.analyser.LocalEnv.LocalVar.localVar;
import static rho.analyser.ValueExpr.LetExpr.LetBinding.letBinding;

public class Analyser {

    static ValueExpr analyseValueExpr(Env env, LocalEnv localEnv, Form form) {
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
                return ValueExpr.VectorExpr.vectorExpr(form.range, form.forms.stream().map(f -> analyseValueExpr(env, localEnv, f)).collect(toPVector()));
            }

            @Override
            public ValueExpr visit(Form.SetForm form) {
                return ValueExpr.SetExpr.setExpr(form.range, form.forms.stream().map(f -> analyseValueExpr(env, localEnv, f)).collect(toPVector()));
            }

            @Override
            public ValueExpr visit(Form.ListForm form) {
                PVector<Form> forms = form.forms;

                if (!forms.isEmpty()) {
                    Form firstForm = forms.get(0);

                    if (firstForm instanceof Form.SymbolForm) {
                        Symbol sym = ((Form.SymbolForm) firstForm).sym;

                        switch (sym.sym) {
                            case "let":
                                if (forms.size() == 3 && forms.get(1) instanceof Form.VectorForm) {
                                    PVector<Form> bindingForms = ((Form.VectorForm) forms.get(1)).forms;

                                    if (bindingForms.size() % 2 != 0) {
                                        throw new UnsupportedOperationException();
                                    }

                                    LocalEnv localEnv_ = localEnv;
                                    List<ValueExpr.LetExpr.LetBinding> bindings = new LinkedList<>();

                                    for (int i = 0; i < bindingForms.size(); i += 2) {
                                        if (bindingForms.get(i) instanceof Form.SymbolForm) {
                                            Symbol bindingSym = ((Form.SymbolForm) bindingForms.get(i)).sym;
                                            LocalEnv.LocalVar localVar = localVar(bindingSym);
                                            localEnv_ = localEnv_.withLocal(bindingSym, localVar);
                                            bindings.add(letBinding(bindingSym, analyseValueExpr(env, localEnv_, bindingForms.get(i + 1))));
                                        } else {
                                            throw new UnsupportedOperationException();
                                        }
                                    }

                                    return new ValueExpr.LetExpr(form.range, TreePVector.from(bindings), analyseValueExpr(env, localEnv_, forms.get(2)));
                                }

                                throw new UnsupportedOperationException();
                            default:
                                Var var =
                                    Optional.ofNullable(env.vars.get(sym))
                                        .orElseThrow(UnsupportedOperationException::new);

                                return new ValueExpr.CallExpr(form.range, var,
                                    forms.subList(1, forms.size()).stream().map(f -> analyseValueExpr(env, localEnv, f)).collect(toPVector()));
                        }
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr visit(Form.SymbolForm form) {
                LocalEnv.LocalVar localVar = localEnv.localVars.get(form.sym);
                if (localVar != null) {
                    return new LocalVarExpr(form.range, localVar);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    static Expr analyse0(Env env, LocalEnv localEnv, Form form) {
        return form.accept(new FormVisitor<Expr>() {
            @Override
            public Expr visit(Form.BoolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr visit(Form.StringForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr visit(Form.IntForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr visit(Form.VectorForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr visit(Form.SetForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr visit(Form.ListForm form) {
                // TODO this isn't always going to be a ValueExpr
                return analyseValueExpr(env, localEnv, form);
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

    public static Expr analyse(Env env, Form form) {
        return analyse0(env, LocalEnv.EMPTY_ENV, form);
    }
}
