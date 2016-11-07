package rho.analyser;

import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.analyser.ValueExpr.LetExpr.LetBinding;
import rho.analyser.ValueExpr.LocalVarExpr;
import rho.reader.Form;
import rho.reader.FormVisitor;
import rho.runtime.Env;
import rho.runtime.Symbol;
import rho.runtime.Var;
import rho.types.ValueTypeHole;

import java.util.LinkedList;
import java.util.List;

import static rho.Util.toPVector;

public class Analyser {

    static ValueExpr<ValueTypeHole> analyseValueExpr(Env env, LocalEnv localEnv, Form form) {
        return form.accept(new FormVisitor<ValueExpr<ValueTypeHole>>() {
            @Override
            public ValueExpr<ValueTypeHole> visit(Form.BoolForm form) {
                return new ValueExpr.BoolExpr<>(form.range, null, form.value);
            }

            @Override
            public ValueExpr<ValueTypeHole> visit(Form.StringForm form) {
                return new ValueExpr.StringExpr<>(form.range, null, form.string);
            }

            @Override
            public ValueExpr<ValueTypeHole> visit(Form.IntForm form) {
                return new ValueExpr.IntExpr<>(form.range, null, form.num);
            }

            @Override
            public ValueExpr<ValueTypeHole> visit(Form.VectorForm form) {
                return new ValueExpr.VectorExpr<>(form.range, null, form.forms.stream().map(f -> analyseValueExpr(env, localEnv, f)).collect(toPVector()));
            }

            @Override
            public ValueExpr<ValueTypeHole> visit(Form.SetForm form) {
                return new ValueExpr.SetExpr<>(form.range, null, form.forms.stream().map(f -> analyseValueExpr(env, localEnv, f)).collect(toPVector()));
            }

            @Override
            public ValueExpr<ValueTypeHole> visit(Form.ListForm form) {
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
                                    List<LetBinding<ValueTypeHole>> bindings = new LinkedList<>();

                                    for (int i = 0; i < bindingForms.size(); i += 2) {
                                        if (bindingForms.get(i) instanceof Form.SymbolForm) {
                                            Symbol bindingSym = ((Form.SymbolForm) bindingForms.get(i)).sym;
                                            LocalVar<ValueTypeHole> localVar = new LocalVar<>(null, bindingSym);
                                            localEnv_ = localEnv_.withLocal(bindingSym, localVar);
                                            bindings.add(new LetBinding<>(null, localVar, analyseValueExpr(env, localEnv_, bindingForms.get(i + 1))));
                                        } else {
                                            throw new UnsupportedOperationException();
                                        }
                                    }

                                    return new ValueExpr.LetExpr<>(form.range, null, TreePVector.from(bindings), analyseValueExpr(env, localEnv_, forms.get(2)));
                                }

                                throw new UnsupportedOperationException();

                            case "if":
                                if (forms.size() == 4) {
                                    return new ValueExpr.IfExpr<>(form.range, null,
                                        analyseValueExpr(env, localEnv, forms.get(1)),
                                        analyseValueExpr(env, localEnv, forms.get(2)),
                                        analyseValueExpr(env, localEnv, forms.get(3)));
                                }

                                throw new UnsupportedOperationException();

                            default:
                                LocalVar localVar = localEnv.localVars.get(sym);
                                if (localVar != null) {
                                    return new ValueExpr.CallExpr<>(form.range, null, forms.stream().map(f -> analyseValueExpr(env, localEnv, f)).collect(toPVector()));
                                }

                                Var var = env.vars.get(sym);
                                if (var != null) {
                                    return new ValueExpr.VarCallExpr<>(form.range, null, var,
                                        forms.subList(1, forms.size()).stream().map(f -> analyseValueExpr(env, localEnv, f)).collect(toPVector()));

                                }

                                throw new UnsupportedOperationException();
                        }
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr<ValueTypeHole> visit(Form.SymbolForm form) {
                LocalVar localVar = localEnv.localVars.get(form.sym);
                if (localVar != null) {
                    return new LocalVarExpr<>(form.range, null, localVar);
                }

                Var var = env.vars.get(form.sym);

                if (var != null) {
                    return new ValueExpr.GlobalVarExpr<>(form.range, null, var);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public ValueExpr<ValueTypeHole> visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    static Expr<ValueTypeHole> analyse0(Env env, LocalEnv localEnv, Form form) {
        return form.accept(new FormVisitor<Expr<ValueTypeHole>>() {
            @Override
            public Expr<ValueTypeHole> visit(Form.BoolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<ValueTypeHole> visit(Form.StringForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr<ValueTypeHole> visit(Form.IntForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr<ValueTypeHole> visit(Form.VectorForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr<ValueTypeHole> visit(Form.SetForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr<ValueTypeHole> visit(Form.ListForm form) {
                Form firstForm = form.forms.get(0);
                if (firstForm instanceof Form.SymbolForm) {
                    switch (((Form.SymbolForm) firstForm).sym.sym) {
                        case "def":
                            if (form.forms.size() == 3 && form.forms.get(1) instanceof Form.SymbolForm) {

                                return new ActionExpr.DefExpr<>(form.range,
                                    ((Form.SymbolForm) form.forms.get(1)).sym,
                                    analyseValueExpr(env, localEnv, form.forms.get(2)));
                            }

                            throw new UnsupportedOperationException();
                        default:
                            return analyseValueExpr(env, localEnv, form);
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<ValueTypeHole> visit(Form.SymbolForm form) {
                return analyseValueExpr(env, localEnv, form);
            }

            @Override
            public Expr<ValueTypeHole> visit(Form.QSymbolForm form) {
                return analyseValueExpr(env, localEnv, form);
            }
        });
    }

    public static Expr<ValueTypeHole> analyse(Env env, Form form) {
        return analyse0(env, LocalEnv.EMPTY_ENV, form);
    }
}
