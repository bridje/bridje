package rho.compiler;

import org.pcollections.*;
import rho.Panic;
import rho.analyser.*;
import rho.runtime.Env;
import rho.runtime.EvalResult;
import rho.runtime.IndyBootstrap;
import rho.types.Type;
import rho.types.TypedExprData;
import rho.util.Pair;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.lang.invoke.MethodHandles.publicLookup;
import static rho.Panic.panic;
import static rho.Util.setOf;
import static rho.Util.*;
import static rho.Util.vectorOf;
import static rho.compiler.AccessFlag.*;
import static rho.compiler.ClassDefiner.defineClass;
import static rho.compiler.Instructions.FieldOp.*;
import static rho.compiler.Instructions.*;
import static rho.compiler.Locals.instanceLocals;
import static rho.compiler.Locals.staticLocals;
import static rho.compiler.NewClass.newBootstrapClass;
import static rho.compiler.NewClass.newClass;
import static rho.compiler.NewField.newField;
import static rho.compiler.NewMethod.newMethod;
import static rho.runtime.Var.*;

public class Compiler {

    private static PMap<LocalVar, Type> closedOverVars(PSet<LocalVar> localVars, ValueExpr<? extends TypedExprData> expr) {
        return expr.accept(new ValueExprVisitor<TypedExprData, PMap<LocalVar, Type>>() {

            private PMap<LocalVar, Type> closedOverVars(PCollection<? extends ValueExpr<? extends TypedExprData>> exprs) {
                return exprs.stream().flatMap(e -> e.accept(this).entrySet().stream()).collect(toPMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.BoolExpr<? extends TypedExprData> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.StringExpr<? extends TypedExprData> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.IntExpr<? extends TypedExprData> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.VectorExpr<? extends TypedExprData> expr) {
                return closedOverVars(expr.exprs);
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.SetExpr<? extends TypedExprData> expr) {
                return closedOverVars(expr.exprs);
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.CallExpr<? extends TypedExprData> expr) {
                return closedOverVars(expr.exprs);
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.VarCallExpr<? extends TypedExprData> expr) {
                return closedOverVars(expr.params);
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.LetExpr<? extends TypedExprData> expr) {
                return closedOverVars(
                    expr.bindings.stream().map(b -> b.expr).collect(toPVector()))
                    .plusAll(expr.body.accept(this));
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.IfExpr<? extends TypedExprData> expr) {
                return expr.testExpr.accept(this)
                    .plusAll(expr.thenExpr.accept(this))
                    .plusAll(expr.elseExpr.accept(this));
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.LocalVarExpr<? extends TypedExprData> expr) {
                if (localVars.contains(expr.localVar)) {
                    return HashTreePMap.singleton(expr.localVar, expr.data.type);
                } else {
                    return Empty.map();
                }
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.GlobalVarExpr<? extends TypedExprData> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(ValueExpr.FnExpr<? extends TypedExprData> expr) {
                return expr.body.accept(this);
            }
        });
    }

    static Instructions compileValue(Locals locals, ValueExpr<? extends TypedExprData> expr) {
        return expr.accept(new ValueExprVisitor<TypedExprData, Instructions>() {
            @Override
            public Instructions visit(ValueExpr.BoolExpr<? extends TypedExprData> expr) {
                return loadBool(expr.value);
            }

            @Override
            public Instructions visit(ValueExpr.StringExpr<? extends TypedExprData> expr) {
                return loadObject(expr.string);
            }

            @Override
            public Instructions visit(ValueExpr.IntExpr<? extends TypedExprData> expr) {
                return loadObject(expr.num);
            }

            @Override
            public Instructions visit(ValueExpr.VectorExpr<? extends TypedExprData> expr) {
                return Instructions.vectorOf(((Type.VectorType) expr.data.type).elemType.javaType(),
                    expr.exprs.stream().map(e -> compileValue(locals, e)).collect(toPVector()));
            }

            @Override
            public Instructions visit(ValueExpr.SetExpr<? extends TypedExprData> expr) {
                return Instructions.setOf(((Type.SetType) expr.data.type).elemType.javaType(),
                    expr.exprs.stream().map(e -> compileValue(locals, e)).collect(toPVector()));
            }

            @Override
            public Instructions visit(ValueExpr.CallExpr<? extends TypedExprData> expr) {
                PVector<? extends ValueExpr<? extends TypedExprData>> params = expr.exprs;

                ValueExpr<? extends TypedExprData> fn = params.get(0);
                PVector<? extends ValueExpr<? extends TypedExprData>> args = expr.exprs.minus(0);

                Instructions fnInstructions = compileValue(locals, fn);

                PVector<Instructions> paramInstructions = args.stream().map(p -> compileValue(locals, p)).collect(toPVector());

                return mplus(
                    fnInstructions,
                    mplus(paramInstructions),
                    methodCall(MethodHandle.class, MethodInvoke.INVOKE_VIRTUAL, "invoke", expr.data.type.javaType(), args.stream().map(a -> a.data.type.javaType()).collect(toPVector())));


            }

            @Override
            public Instructions visit(ValueExpr.VarCallExpr<? extends TypedExprData> expr) {
                return varCall(expr.var, expr.params.stream().map(p -> compileValue(locals, p)).collect(toPVector()));
            }

            @Override
            public Instructions visit(ValueExpr.LetExpr<? extends TypedExprData> expr) {
                List<Instructions> bindingsInstructions = new LinkedList<>();
                Locals locals_ = locals;

                for (ValueExpr.LetExpr.LetBinding<? extends TypedExprData> binding : expr.bindings) {
                    Instructions bindingInstructions = compileValue(locals_, binding.expr);

                    Pair<Locals, Locals.Local.VarLocal> withNewLocal = locals_.newVarLocal(binding.localVar, binding.expr.data.type.javaType());
                    locals_ = withNewLocal.left;
                    Locals.Local.VarLocal local = withNewLocal.right;

                    bindingsInstructions.add(letBinding(bindingInstructions, binding.expr.data.type.javaType(), local));
                }

                Instructions bodyInstructions = compileValue(locals_, expr.body);

                return mplus(mplus(TreePVector.from(bindingsInstructions)), bodyInstructions);
            }

            @Override
            public Instructions visit(ValueExpr.IfExpr<? extends TypedExprData> expr) {
                return ifCall(compileValue(locals, expr.testExpr), compileValue(locals, expr.thenExpr), compileValue(locals, expr.elseExpr));
            }

            @Override
            public Instructions visit(ValueExpr.LocalVarExpr<? extends TypedExprData> expr) {
                return localVarCall(locals.locals.get(expr.localVar));
            }

            @Override
            public Instructions visit(ValueExpr.GlobalVarExpr<? extends TypedExprData> expr) {
                return globalVarValue(expr.var);
            }

            @Override
            public Instructions visit(ValueExpr.FnExpr<? extends TypedExprData> expr) {
                Type.FnType fnType = (Type.FnType) expr.data.type;
                String className = "user$$fn$$" + uniqueInt();

                PVector<Type> paramTypes = fnType.paramTypes;
                PVector<Class<?>> paramClasses = Empty.vector();
                Class<?> returnClass = fnType.returnType.javaType();
                Locals fnLocals = instanceLocals();

                PMap<LocalVar, Type> closedOverVars = closedOverVars(HashTreePSet.from(locals.locals.keySet()), expr.body);
                PMap<LocalVar, String> closedOverFieldNames = closedOverVars.entrySet().stream().collect(
                    toPMap(
                        Map.Entry::getKey,
                        e -> String.format("%s$$%d", e.getKey().sym.sym, uniqueInt())));

                PVector<LocalVar> closedOverParamOrder = closedOverVars.entrySet().stream().map(Map.Entry::getKey).collect(toPVector());
                PVector<Class<?>> closedOverParamClasses = closedOverParamOrder.stream().map(p -> closedOverVars.get(p).javaType()).collect(toPVector());

                for (int i = 0; i < paramTypes.size(); i++) {
                    Type paramType = paramTypes.get(i);
                    Class<?> paramClass = paramType.javaType();

                    paramClasses = paramClasses.plus(paramClass);
                    fnLocals = fnLocals.newVarLocal(expr.params.get(i), paramClass).left;
                }

                for (LocalVar closedOverVar : closedOverParamOrder) {
                    fnLocals = fnLocals.newFieldLocal(closedOverVar, closedOverVars.get(closedOverVar).javaType(), className, closedOverFieldNames.get(closedOverVar)).left;
                }

                Instructions bodyInstructions = compileValue(fnLocals, expr.body);

                NewClass newClass = newClass(className);

                for (LocalVar closedOverVar : closedOverParamOrder) {
                    newClass = newClass.withField(newField(closedOverFieldNames.get(closedOverVar), closedOverVars.get(closedOverVar).javaType(), setOf(PRIVATE, FINAL)));
                }

                Instructions constructorInstructions =
                    mplus(loadThis(),
                        methodCall(Object.class, MethodInvoke.INVOKE_SPECIAL, "<init>", Void.TYPE, Empty.vector())
                    );

                Locals constructorLocals = instanceLocals();

                for (LocalVar localVar : closedOverParamOrder) {
                    Class<?> paramClass = closedOverVars.get(localVar).javaType();
                    Pair<Locals, Locals.Local.VarLocal> paramLocalResult = constructorLocals.newVarLocal(localVar, paramClass);
                    constructorLocals = paramLocalResult.left;
                    constructorInstructions = mplus(constructorInstructions,
                        loadThis(),
                        localVarCall(paramLocalResult.right),
                        fieldOp(PUT_FIELD, className, closedOverFieldNames.get(localVar), paramClass));
                }

                Class<?> fnClass = defineClass(newClass
                    .withMethod(newMethod(setOf(PUBLIC), "<init>", Void.TYPE, closedOverParamClasses,
                        mplus(constructorInstructions, ret(Void.TYPE))))
                    .withMethod(newMethod(setOf(PUBLIC, FINAL), "$$fn",
                        returnClass,
                        paramClasses,
                        mplus(
                            bodyInstructions,
                            ret(returnClass)))));


                return mplus(
                    virtualMethodHandle(fnClass, "$$fn", paramClasses, returnClass),

                    newObject(fnClass, closedOverParamClasses,
                        mplus(closedOverParamOrder.stream()
                            .map(p -> localVarCall(locals.locals.get(p)))
                            .collect(toPVector()))),

                    bindMethodHandle());
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
                                    newMethod(setOf(PUBLIC, FINAL, STATIC), "$$eval", Object.class, vectorOf(), mplus(instructions, box(org.objectweb.asm.Type.getType(returnType)), ret(Object.class))))),


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

    public static EvalResult compile(Env env, Expr<? extends TypedExprData> expr) {
        return expr.accept(new ExprVisitor<TypedExprData, EvalResult>() {
            @Override
            public EvalResult accept(ValueExpr<? extends TypedExprData> expr) {
                return new EvalResult(env, evalInstructions(compileValue(staticLocals(), expr), expr.data.type.javaType()));
            }

            @Override
            public EvalResult accept(ActionExpr<? extends TypedExprData> expr) {
                return expr.accept(new ActionExprVisitor<TypedExprData, EvalResult>() {
                    @Override
                    public EvalResult visit(ActionExpr.DefExpr<? extends TypedExprData> expr) {
                        String className = String.format("user$$%s$$%d", expr.sym, uniqueInt());
                        Type type = expr.body.data.type;
                        Class<?> clazz = type.javaType();

                        boolean isFn = expr.body instanceof ValueExpr.FnExpr && type instanceof Type.FnType;

                        ValueExpr.FnExpr<? extends TypedExprData> fnExpr = null;
                        Type.FnType fnType = null;
                        PVector<Class<?>> paramTypes = null;
                        MethodType fnMethodType = null;

                        if (isFn) {
                            fnType = (Type.FnType) type;
                            fnExpr = (ValueExpr.FnExpr<? extends TypedExprData>) expr.body;
                            paramTypes = fnType.paramTypes.stream().map(Type::javaType).collect(toPVector());
                            fnMethodType = MethodType.methodType(
                                fnType.returnType.javaType(),
                                paramTypes);
                        }

                        Instructions valueInstructions = isFn
                            ? Instructions.staticMethodHandle(toInternalName(className), FN_METHOD_NAME, paramTypes, fnMethodType.returnType())
                            : compileValue(staticLocals(), expr.body);

                        NewClass newClass = newClass(className)
                            .withField(newField(VALUE_METHOD_NAME, clazz, setOf(STATIC, FINAL, PRIVATE)))
                            .withMethod(newMethod(setOf(PUBLIC, STATIC), VALUE_METHOD_NAME, clazz, vectorOf(),
                                mplus(
                                    fieldOp(GET_STATIC, className, VALUE_METHOD_NAME, clazz),
                                    ret(clazz))))
                            .withMethod(newMethod(setOf(STATIC), "<clinit>", Void.TYPE, vectorOf(),
                                mplus(valueInstructions,
                                    fieldOp(PUT_STATIC, className, VALUE_METHOD_NAME, clazz),
                                    ret(Void.TYPE))));

                        if (isFn) {
                            Locals locals = staticLocals();
                            for (int i = 0; i < fnExpr.params.size(); i++) {
                                locals = locals.newVarLocal(fnExpr.params.get(i), fnType.paramTypes.get(i).javaType()).left;
                            }

                            newClass = newClass.withMethod(newMethod(setOf(STATIC, PUBLIC), FN_METHOD_NAME, fnMethodType.returnType(), paramTypes,
                                mplus(
                                    compileValue(locals, fnExpr.body),
                                    ret(fnType.returnType.javaType()))));
                        }

                        Class<?> dynClass = defineClass(newClass);
                        MethodHandle valueHandle;
                        MethodHandle fnHandle = null;

                        try {
                            valueHandle = publicLookup().findStatic(dynClass, VALUE_METHOD_NAME, MethodType.methodType(clazz));
                        } catch (NoSuchMethodException | IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }


                        if (isFn) {
                            try {
                                fnHandle = publicLookup().findStatic(dynClass, FN_METHOD_NAME, fnMethodType);
                            } catch (NoSuchMethodException | IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @SuppressWarnings("unchecked")
                        Class<? extends IndyBootstrap> bootstrapClass = (Class<? extends IndyBootstrap>) defineClass(newBootstrapClass(type));

                        IndyBootstrap bootstrap;
                        try {
                            bootstrap = bootstrapClass.newInstance();
                        } catch (InstantiationException | IllegalAccessException e) {
                            throw panic(e, "Failed instantiating bootstrap class");
                        }


                        bootstrap.setHandles(valueHandle, fnHandle);

                        Env newEnv = env.withVar(expr.sym, var(type, bootstrap, fnMethodType));

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
