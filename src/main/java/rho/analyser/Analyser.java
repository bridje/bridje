package rho.analyser;

import org.pcollections.Empty;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.analyser.Expr.LetExpr.LetBinding;
import rho.analyser.Expr.LocalVarExpr;
import rho.reader.Form;
import rho.reader.FormVisitor;
import rho.runtime.Env;
import rho.runtime.Symbol;
import rho.runtime.Var;
import rho.types.Type;

import java.util.LinkedList;
import java.util.List;

import static rho.Util.toPVector;

public class Analyser {

    static Expr<Void> analyse0(Env env, LocalEnv localEnv, Form form) {
        return form.accept(new FormVisitor<Expr<Void>>() {
            @Override
            public Expr<Void> visit(Form.BoolForm form) {
                return new Expr.BoolExpr<>(form.range, null, form.value);
            }

            @Override
            public Expr<Void> visit(Form.StringForm form) {
                return new Expr.StringExpr<>(form.range, null, form.string);
            }

            @Override
            public Expr<Void> visit(Form.IntForm form) {
                return new Expr.IntExpr<>(form.range, null, form.num);
            }

            @Override
            public Expr<Void> visit(Form.VectorForm form) {
                return new Expr.VectorExpr<>(form.range, null, form.forms.stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector()));
            }

            @Override
            public Expr<Void> visit(Form.SetForm form) {
                return new Expr.SetExpr<>(form.range, null, form.forms.stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector()));
            }

            @Override
            public Expr<Void> visit(Form.ListForm form) {
                PVector<Form> forms = form.forms;
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
                                List<LetBinding<Void>> bindings = new LinkedList<>();

                                for (int i = 0; i < bindingForms.size(); i += 2) {
                                    if (bindingForms.get(i) instanceof Form.SymbolForm) {
                                        Symbol bindingSym = ((Form.SymbolForm) bindingForms.get(i)).sym;
                                        LocalVar localVar = new LocalVar(bindingSym);
                                        localEnv_ = localEnv_.withLocal(bindingSym, localVar);
                                        bindings.add(new LetBinding<>(localVar, analyse0(env, localEnv_, bindingForms.get(i + 1))));
                                    } else {
                                        throw new UnsupportedOperationException();
                                    }
                                }

                                return new Expr.LetExpr<>(form.range, null, TreePVector.from(bindings), analyse0(env, localEnv_, forms.get(2)));
                            }

                            throw new UnsupportedOperationException();

                        case "if":
                            if (forms.size() == 4) {
                                return new Expr.IfExpr<>(form.range, null,
                                    analyse0(env, localEnv, forms.get(1)),
                                    analyse0(env, localEnv, forms.get(2)),
                                    analyse0(env, localEnv, forms.get(3)));
                            }

                            throw new UnsupportedOperationException();

                        case "fn":
                            if (forms.size() == 3) {
                                Form paramsForm = forms.get(1);
                                if (paramsForm instanceof Form.ListForm) {
                                    LocalEnv localEnv_ = localEnv;
                                    PVector<LocalVar> paramLocals = Empty.vector();

                                    for (Form paramForm : ((Form.ListForm) paramsForm).forms) {
                                        if (paramForm instanceof Form.SymbolForm) {
                                            Symbol paramSym = ((Form.SymbolForm) paramForm).sym;
                                            LocalVar localVar = new LocalVar(paramSym);
                                            localEnv_ = localEnv_.withLocal(paramSym, localVar);
                                            paramLocals = paramLocals.plus(localVar);
                                        } else {
                                            throw new UnsupportedOperationException();
                                        }
                                    }

                                    return new Expr.FnExpr<>(form.range, null, paramLocals, analyse0(env, localEnv_, forms.get(2)));
                                }
                            }

                            throw new UnsupportedOperationException();

                        
                        case "def":
                            if (forms.size() == 3) {
                                Form nameForm = forms.get(1);
                                Form bodyForm = forms.get(2);

                                if (nameForm instanceof Form.SymbolForm) {
                                    return new Expr.DefExpr<>(form.range, null,
                                        ((Form.SymbolForm) nameForm).sym,
                                        analyse0(env, localEnv, bodyForm));

                                } else if (nameForm instanceof Form.ListForm) {
                                    PVector<Form> nameFormForms = ((Form.ListForm) nameForm).forms;

                                    if (nameFormForms.get(0) instanceof Form.SymbolForm) {
                                        Symbol name = ((Form.SymbolForm) nameFormForms.get(0)).sym;
                                        PVector<LocalVar> params = Empty.vector();
                                        LocalEnv localEnv_ = localEnv;

                                        for (Form paramForm : nameFormForms.minus(0)) {
                                            if (paramForm instanceof Form.SymbolForm) {
                                                Symbol nameSym = ((Form.SymbolForm) paramForm).sym;
                                                LocalVar localVar = new LocalVar(nameSym);
                                                params = params.plus(localVar);
                                                localEnv_ = localEnv_.withLocal(nameSym, localVar);
                                            } else {
                                                throw new UnsupportedOperationException();
                                            }
                                        }

                                        return new Expr.DefExpr<>(form.range, null, name, new Expr.FnExpr<>(form.range, null, params, analyse0(env, localEnv_, bodyForm)));
                                    } else {
                                        throw new UnsupportedOperationException();
                                    }
                                } else {
                                    throw new UnsupportedOperationException();
                                }


                            }

                        case "::":
                            if (forms.size() == 3) {
                                Form symForm = forms.get(1);
                                Form typeForm = forms.get(2);

                                if (symForm instanceof Form.SymbolForm) {
                                    Symbol typeDefSym = ((Form.SymbolForm) symForm).sym;
                                    Type type = TypeAnalyser.analyzeType(typeForm);
                                    return new Expr.TypeDefExpr<>(form.range, null, typeDefSym, type);
                                }
                            }

                            throw new UnsupportedOperationException();

                        default:
                            LocalVar localVar = localEnv.localVars.get(sym);
                            if (localVar != null) {
                                return new Expr.CallExpr<>(form.range, null, forms.stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector()));
                            }

                            Var var = env.vars.get(sym);
                            if (var != null) {
                                return new Expr.VarCallExpr<>(form.range, null, var,
                                    forms.subList(1, forms.size()).stream().map(f -> analyse0(env, localEnv, f)).collect(toPVector()));

                            }
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<Void> visit(Form.SymbolForm form) {
                LocalVar localVar = localEnv.localVars.get(form.sym);
                if (localVar != null) {
                    return new LocalVarExpr<>(form.range, null, localVar);
                }

                Var var = env.vars.get(form.sym);

                if (var != null) {
                    return new Expr.GlobalVarExpr<>(form.range, null, var);
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Expr<Void> visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Expr<Void> analyse(Env env, Form form) {
        return analyse0(env, LocalEnv.EMPTY_ENV, form);
    }
}
