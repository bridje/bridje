package rho.compiler;

import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.HashTreePSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import rho.Panic;
import rho.analyser.*;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.runtime.IndyBootstrap;
import rho.types.ValueType;
import rho.util.Pair;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.lang.invoke.MethodHandles.publicLookup;
import static rho.Panic.panic;
import static rho.Util.setOf;
import static rho.Util.*;
import static rho.Util.vectorOf;
import static rho.compiler.AccessFlag.*;
import static rho.compiler.ClassDefiner.defineClass;
import static rho.compiler.Instructions.FieldOp.GET_STATIC;
import static rho.compiler.Instructions.FieldOp.PUT_STATIC;
import static rho.compiler.Instructions.*;
import static rho.compiler.Locals.staticLocals;
import static rho.compiler.NewClass.newBootstrapClass;
import static rho.compiler.NewClass.newClass;
import static rho.compiler.NewField.newField;
import static rho.compiler.NewMethod.newMethod;
import static rho.runtime.Var.var;

public class Compiler {

    static CompileResult compileValue(Locals locals, ValueExpr<? extends ValueType> expr) {
        return expr.accept(new ValueExprVisitor<CompileResult, ValueType>() {
            @Override
            public CompileResult visit(ValueExpr.BoolExpr<? extends ValueType> expr) {
                return new CompileResult(loadBool(expr.value), setOf());
            }

            @Override
            public CompileResult visit(ValueExpr.StringExpr<? extends ValueType> expr) {
                return new CompileResult(loadObject(expr.string), setOf());
            }

            @Override
            public CompileResult visit(ValueExpr.IntExpr<? extends ValueType> expr) {
                return new CompileResult(
                    loadObject(expr.num),
                    setOf());
            }

            @Override
            public CompileResult visit(ValueExpr.VectorExpr<? extends ValueType> expr) {
                List<Instructions> instructions = new LinkedList<>();
                Set<NewClass> newClasses = new HashSet<>();

                for (ValueExpr<? extends ValueType> innerExpr : expr.exprs) {
                    CompileResult compileResult = compileValue(locals, innerExpr);
                    instructions.add(compileResult.instructions);
                    newClasses.addAll(compileResult.newClasses);
                }

                return new CompileResult(Instructions.vectorOf(((ValueType.VectorType) expr.data).elemType.javaType(), TreePVector.from(instructions)), HashTreePSet.from(newClasses));
            }

            @Override
            public CompileResult visit(ValueExpr.SetExpr<? extends ValueType> expr) {
                List<Instructions> instructions = new LinkedList<>();
                Set<NewClass> newClasses = new HashSet<>();

                for (ValueExpr<? extends ValueType> innerExpr : expr.exprs) {
                    CompileResult compileResult = compileValue(locals, innerExpr);
                    instructions.add(compileResult.instructions);
                    newClasses.addAll(compileResult.newClasses);
                }

                return new CompileResult(Instructions.setOf(((ValueType.SetType) expr.data).elemType.javaType(), TreePVector.from(instructions)), HashTreePSet.from(newClasses));
            }

            @Override
            public CompileResult visit(ValueExpr.CallExpr<? extends ValueType> expr) {
                PVector<? extends ValueExpr<? extends ValueType>> params = expr.params;

                ValueExpr<? extends ValueType> fn = params.get(0);
                PVector<? extends ValueExpr<? extends ValueType>> args = expr.params.minus(0);

                CompileResult fnCompileResult = compileValue(locals, fn);

                PVector<CompileResult> paramCompileResults = args.stream().map(p -> compileValue(locals, p)).collect(toPVector());

                return new CompileResult(
                    mplus(
                        fnCompileResult.instructions,
                        mplus(paramCompileResults.stream().map(pcr -> pcr.instructions).collect(toPVector())),
                        methodCall(MethodHandle.class, MethodInvoke.INVOKE_VIRTUAL, "invoke", expr.data.javaType(), args.stream().map(a -> a.data.javaType()).collect(toPVector()))),
                    fnCompileResult.newClasses
                        .plusAll(paramCompileResults.stream().flatMap(pcr -> pcr.newClasses.stream()).collect(toPSet())));


            }

            @Override
            public CompileResult visit(ValueExpr.VarCallExpr<? extends ValueType> expr) {
                List<Instructions> paramInstructions = new LinkedList<>();
                Set<NewClass> newClasses = new HashSet<>();

                for (ValueExpr<? extends ValueType> param : expr.params) {
                    CompileResult compileResult = compileValue(locals, param);
                    paramInstructions.add(compileResult.instructions);

                }

                return new CompileResult(varCall(expr.var, TreePVector.from(paramInstructions)), HashTreePSet.from(newClasses));
            }

            @Override
            public CompileResult visit(ValueExpr.LetExpr<? extends ValueType> expr) {
                List<Instructions> bindingsInstructions = new LinkedList<>();
                Set<NewClass> newClasses = new HashSet<>();
                Locals locals_ = locals;

                for (ValueExpr.LetExpr.LetBinding<? extends ValueType> binding : expr.bindings) {
                    CompileResult bindingResult = compileValue(locals_, binding.expr);
                    newClasses.addAll(bindingResult.newClasses);

                    Pair<Locals, Locals.Local> withNewLocal = locals_.newLocal(binding.localVar, binding.expr.data.javaType());
                    locals_ = withNewLocal.left;
                    Locals.Local local = withNewLocal.right;

                    bindingsInstructions.add(letBinding(bindingResult.instructions, binding.expr.data.javaType(), local));
                }

                CompileResult bodyCompileResult = compileValue(locals_, expr.body);
                newClasses.addAll(bodyCompileResult.newClasses);

                return new CompileResult(mplus(mplus(TreePVector.from(bindingsInstructions)), bodyCompileResult.instructions), HashTreePSet.from(newClasses));
            }

            @Override
            public CompileResult visit(ValueExpr.IfExpr<? extends ValueType> expr) {
                CompileResult compiledTest = compileValue(locals, expr.testExpr);
                CompileResult compiledThen = compileValue(locals, expr.thenExpr);
                CompileResult compiledElse = compileValue(locals, expr.elseExpr);

                return new CompileResult(
                    ifCall(compiledTest.instructions, compiledThen.instructions, compiledElse.instructions),
                    compiledTest.newClasses.plusAll(compiledThen.newClasses).plusAll(compiledElse.newClasses));
            }

            @Override
            public CompileResult visit(ValueExpr.LocalVarExpr<? extends ValueType> expr) {
                return new CompileResult(localVarCall(locals.locals.get(expr.localVar)), Empty.set());
            }

            @Override
            public CompileResult visit(ValueExpr.GlobalVarExpr<? extends ValueType> expr) {
                return new CompileResult(globalVarValue(expr.var), Empty.set());
            }
        });
    }

    private static Object evalInstructions(Instructions instructions, Class<?> returnType) {
        try {
            return
                publicLookup()
                    .findStatic(
                        defineClass(
                            newClass("user$$eval$$" + uniqueInt())
                                .withMethod(
                                    newMethod("$$eval", Object.class, vectorOf(), mplus(instructions, box(Type.getType(returnType)), ret(Object.class)))
                                        .withFlags(setOf(PUBLIC, FINAL, STATIC)))),


                        "$$eval",
                        MethodType.methodType(Object.class))
                    .invoke();
        } catch (Panic e) {
            throw e;
        } catch (InvocationTargetException e) {
            try {
                throw e.getCause();
            } catch (Panic p) {
                throw p;
            } catch (Throwable t) {
                throw panic(t, "Error evaluating $$eval method.");
            }
        } catch (Throwable e) {
            throw panic(e, "Can't execute $$eval method.");
        }
    }

    static EvalResult compile(Env env, Expr<? extends ValueType> expr) {
        return expr.accept(new ExprVisitor<EvalResult, ValueType>() {
            @Override
            public EvalResult accept(ValueExpr<? extends ValueType> expr) {
                CompileResult compileResult = compileValue(staticLocals(), expr);

                compileResult.newClasses.forEach(ClassDefiner::defineClass);

                return new EvalResult(env, evalInstructions(mplus(compileResult.instructions), expr.data.javaType()));
            }

            @Override
            public EvalResult accept(ActionExpr<? extends ValueType> expr) {
                return expr.accept(new ActionExprVisitor<EvalResult, ValueType>() {
                    @Override
                    public EvalResult visit(ActionExpr.DefExpr<? extends ValueType> expr) {
                        CompileResult compileResult = compileValue(staticLocals(), expr.body);

                        compileResult.newClasses.forEach(ClassDefiner::defineClass);

                        String className = String.format("user$$%s$$%d", expr.sym, uniqueInt());

                        ValueType valueType = expr.body.data;

                        Class<?> clazz = valueType.javaType();

                        NewClass newClass = newClass(className)
                            .withField(newField("$$value", clazz, setOf(STATIC, FINAL, PRIVATE)))
                            .withMethod(newMethod("<clinit>", Void.TYPE, vectorOf(),
                                mplus(compileResult.instructions,
                                    fieldOp(PUT_STATIC, className, "$$value", clazz),
                                    ret(Void.TYPE))).withFlags(setOf(STATIC)))

                            .withMethod(newMethod("$$value", clazz, vectorOf(),
                                mplus(
                                    fieldOp(GET_STATIC, className, "$$value", clazz),
                                    ret(clazz))).withFlags(setOf(PUBLIC, STATIC)));

                        Class<?> dynClass = defineClass(newClass);
                        MethodHandle handle;

                        try {
                            handle = publicLookup().findStatic(dynClass, "$$value", MethodType.methodType(clazz));
                        } catch (NoSuchMethodException | IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }

                        @SuppressWarnings("unchecked")
                        Class<? extends IndyBootstrap> bootstrapClass = (Class<? extends IndyBootstrap>) defineClass(newBootstrapClass(valueType));

                        IndyBootstrap bootstrap;
                        try {
                            bootstrap = bootstrapClass.newInstance();
                        } catch (InstantiationException | IllegalAccessException e) {
                            throw panic(e, "Failed instantiating bootstrap class");
                        }

                        bootstrap.setHandles(handle, null);

                        Env newEnv = env.withVar(expr.sym, var(valueType, bootstrap, null));

                        try {
                            return new EvalResult(newEnv, dynClass.getDeclaredMethod("$$value").invoke(null));
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                            throw new RuntimeException(e);
                        }

                    }
                });
            }
        });
    }
}
