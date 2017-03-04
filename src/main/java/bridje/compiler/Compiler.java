package bridje.compiler;

import bridje.Util;
import bridje.analyser.*;
import bridje.runtime.Env;
import bridje.runtime.EvalResult;
import bridje.runtime.NS;
import bridje.runtime.Var;
import bridje.runtime.java.JavaField;
import bridje.util.ClassLike;

import java.util.Collection;
import java.util.LinkedList;

import static bridje.Util.setOf;
import static bridje.compiler.AccessFlag.PUBLIC;
import static bridje.compiler.AccessFlag.STATIC;
import static bridje.compiler.Instructions.*;
import static bridje.compiler.JavaSort.LONG;
import static bridje.compiler.JavaSort.OBJECT;
import static bridje.compiler.NewClass.newClass;
import static bridje.compiler.NewField.newField;
import static bridje.util.ClassLike.fromClass;
import static bridje.util.ClassLike.fromClassName;

public class Compiler {
    private final NS ns;
    private final Collection<NewClass> newClasses = new LinkedList<>();
    private NewClass nsClass;
    private int suffixInt = 0;

    public Compiler(NS ns) {
        nsClass = newClass(fromClassName(ns.name + "__" + Util.uniqueInt()));
        this.ns = ns;
    }

    private Instructions compileValueExpr(Env env, ValueExpr expr, JavaSort coerceTarget) {
        return expr.accept(new ValueExprVisitor<Instructions>() {
            @Override
            public Instructions visit(ValueExpr.BoolExpr expr) {
                return loadBool(expr.value);
            }

            @Override
            public Instructions visit(ValueExpr.StringExpr expr) {
                return loadString(expr.string);
            }

            @Override
            public Instructions visit(ValueExpr.IntExpr expr) {
                return mplus(
                    loadLong(expr.num),
                    coerce(LONG, coerceTarget));
            }

            @Override
            public Instructions visit(ValueExpr.VectorExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.SetExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.CallExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.VarCallExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.LetExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.IfExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.LocalVarExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.GlobalVarExpr expr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(ValueExpr.FnExpr expr) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public EvalResult<?> compile(Env env, Expr expr) {
        return expr.accept(new ExprVisitor<EvalResult<?>>() {
            @Override
            public EvalResult<?> accept(ValueExpr expr) {
                // TODO will need this for eval, I suspect
                throw new UnsupportedOperationException();
            }

            @Override
            public EvalResult<?> accept(ActionExpr expr) {
                return expr.accept(new ActionExprVisitor<EvalResult<?>>() {
                    @Override
                    public EvalResult<?> visit(ActionExpr.DefExpr expr) {
                        if (expr.params.isEmpty()) {
                            String fieldName = expr.sym.symbol.sym + "__value";
                            ClassLike fieldClass = fromClass(Object.class);
                            nsClass = nsClass
                                .withField(newField(setOf(PUBLIC, STATIC), fieldName, fieldClass))
                                .withClinitInstructions(
                                    mplus(
                                        compileValueExpr(env, expr.body, OBJECT),
                                        fieldOp(FieldOp.PUT_STATIC, nsClass.clazz, fieldName, fieldClass)));

                            return new EvalResult<>(
                                env.updateNSEnv(ns, nsEnv -> nsEnv.withVar(expr.sym.symbol, new Var(new JavaField(nsClass.clazz, fieldName, fieldClass)))),
                                expr.sym);

                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }

                    @Override
                    public EvalResult<?> visit(ActionExpr.DefDataExpr expr) {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        });
    }

    public Class<?> build() {
        newClasses.forEach(NewClass::defineClass);

        return nsClass.defineClass();
    }
}
