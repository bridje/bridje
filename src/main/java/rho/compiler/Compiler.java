package rho.compiler;

import org.pcollections.*;
import rho.Panic;
import rho.analyser.Expr;
import rho.analyser.ExprVisitor;
import rho.analyser.LocalVar;
import rho.runtime.*;
import rho.types.Type;
import rho.util.Pair;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.publicLookup;
import static rho.Panic.panic;
import static rho.Util.setOf;
import static rho.Util.*;
import static rho.Util.vectorOf;
import static rho.compiler.AccessFlag.*;
import static rho.compiler.ClassDefiner.defineClass;
import static rho.compiler.ClassLike.fromClass;
import static rho.compiler.ClassLike.fromClassName;
import static rho.compiler.Instructions.FieldOp.PUT_FIELD;
import static rho.compiler.Instructions.FieldOp.PUT_STATIC;
import static rho.compiler.Instructions.*;
import static rho.compiler.Instructions.MethodInvoke.INVOKE_SPECIAL;
import static rho.compiler.Locals.instanceLocals;
import static rho.compiler.Locals.staticLocals;
import static rho.compiler.NewClass.newClass;
import static rho.compiler.NewField.newField;
import static rho.compiler.NewMethod.newMethod;
import static rho.runtime.Var.FN_METHOD_NAME;
import static rho.runtime.Var.VALUE_FIELD_NAME;

public class Compiler {

    private static PMap<LocalVar, Type> closedOverVars(PSet<LocalVar> localVars, Expr<? extends Type> expr) {
        return expr.accept(new ExprVisitor<Type, PMap<LocalVar, Type>>() {

            private PMap<LocalVar, Type> closedOverVars(PCollection<? extends Expr<? extends Type>> exprs) {
                return exprs.stream().flatMap(e -> e.accept(this).entrySet().stream()).collect(toPMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.BoolExpr<? extends Type> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.StringExpr<? extends Type> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.IntExpr<? extends Type> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.VectorExpr<? extends Type> expr) {
                return closedOverVars(expr.exprs);
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.SetExpr<? extends Type> expr) {
                return closedOverVars(expr.exprs);
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.CallExpr<? extends Type> expr) {
                return closedOverVars(expr.exprs);
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.VarCallExpr<? extends Type> expr) {
                return closedOverVars(expr.params);
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.LetExpr<? extends Type> expr) {
                return closedOverVars(
                    expr.bindings.stream().map(b -> b.expr).collect(toPVector()))
                    .plusAll(expr.body.accept(this));
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.IfExpr<? extends Type> expr) {
                return expr.testExpr.accept(this)
                    .plusAll(expr.thenExpr.accept(this))
                    .plusAll(expr.elseExpr.accept(this));
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.LocalVarExpr<? extends Type> expr) {
                if (localVars.contains(expr.localVar)) {
                    return HashTreePMap.singleton(expr.localVar, expr.type);
                } else {
                    return Empty.map();
                }
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.GlobalVarExpr<? extends Type> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.FnExpr<? extends Type> expr) {
                return expr.body.accept(this);
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.DefExpr<? extends Type> expr) {
                return expr.body.accept(this);
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.TypeDefExpr<? extends Type> expr) {
                return Empty.map();
            }

            @Override
            public PMap<LocalVar, Type> visit(Expr.DefDataExpr<? extends Type> expr) {
                return Empty.map();
            }
        });
    }

    private static Instructions compileExpr0(Locals locals, Expr<? extends Type> expr) {
        return expr.accept(new ExprVisitor<Type, Instructions>() {
            @Override
            public Instructions visit(Expr.BoolExpr<? extends Type> expr) {
                return loadBool(expr.value);
            }

            @Override
            public Instructions visit(Expr.StringExpr<? extends Type> expr) {
                return loadObject(expr.string);
            }

            @Override
            public Instructions visit(Expr.IntExpr<? extends Type> expr) {
                return loadObject(expr.num);
            }

            @Override
            public Instructions visit(Expr.VectorExpr<? extends Type> expr) {
                return Instructions.loadVector(((Type.VectorType) expr.type).elemType.javaType,
                    expr.exprs.stream().map(e -> compileExpr0(locals, e)).collect(toPVector()));
            }

            @Override
            public Instructions visit(Expr.SetExpr<? extends Type> expr) {
                return Instructions.loadSet(((Type.SetType) expr.type).elemType.javaType,
                    expr.exprs.stream().map(e -> compileExpr0(locals, e)).collect(toPVector()));
            }

            @Override
            public Instructions visit(Expr.CallExpr<? extends Type> expr) {
                PVector<? extends Expr<? extends Type>> params = expr.exprs;

                Expr<? extends Type> fn = params.get(0);
                PVector<? extends Expr<? extends Type>> args = expr.exprs.minus(0);

                Instructions fnInstructions = compileExpr0(locals, fn);

                PVector<Instructions> paramInstructions = args.stream().map(p -> compileExpr0(locals, p)).collect(toPVector());

                return mplus(
                    fnInstructions,
                    mplus(paramInstructions),
                    methodCall(fromClass(MethodHandle.class), MethodInvoke.INVOKE_VIRTUAL, "invoke", expr.type.javaType, args.stream().map(a -> a.type.javaType).collect(toPVector())));
            }

            @Override
            public Instructions visit(Expr.VarCallExpr<? extends Type> expr) {
                return varCall(expr.var, expr.params.stream().map(p -> compileExpr0(locals, p)).collect(toPVector()));
            }

            @Override
            public Instructions visit(Expr.LetExpr<? extends Type> expr) {
                List<Instructions> bindingsInstructions = new LinkedList<>();
                Locals locals_ = locals;

                for (Expr.LetExpr.LetBinding<? extends Type> binding : expr.bindings) {
                    Instructions bindingInstructions = compileExpr0(locals_, binding.expr);

                    Pair<Locals, Locals.Local.VarLocal> withNewLocal = locals_.newVarLocal(binding.localVar, binding.expr.type.javaType);
                    locals_ = withNewLocal.left;
                    Locals.Local.VarLocal local = withNewLocal.right;

                    bindingsInstructions.add(letBinding(bindingInstructions, binding.expr.type.javaType, local));
                }

                Instructions bodyInstructions = compileExpr0(locals_, expr.body);

                return mplus(mplus(TreePVector.from(bindingsInstructions)), bodyInstructions);
            }

            @Override
            public Instructions visit(Expr.IfExpr<? extends Type> expr) {
                return ifCall(compileExpr0(locals, expr.testExpr), compileExpr0(locals, expr.thenExpr), compileExpr0(locals, expr.elseExpr));
            }

            @Override
            public Instructions visit(Expr.LocalVarExpr<? extends Type> expr) {
                return localVarCall(locals.locals.get(expr.localVar));
            }

            @Override
            public Instructions visit(Expr.GlobalVarExpr<? extends Type> expr) {
                return globalVarValue(expr.var);
            }

            @Override
            public Instructions visit(Expr.FnExpr<? extends Type> expr) {
                Type.FnType fnType = (Type.FnType) expr.type;
                String className = "user$$fn$$" + uniqueInt();

                PVector<Type> paramTypes = fnType.paramTypes;
                PVector<Class<?>> paramClasses = Empty.vector();
                Class<?> returnClass = fnType.returnType.javaType;
                Locals fnLocals = instanceLocals();

                PMap<LocalVar, Type> closedOverVars = closedOverVars(HashTreePSet.from(locals.locals.keySet()), expr.body);
                PMap<LocalVar, String> closedOverFieldNames = closedOverVars.entrySet().stream().collect(
                    toPMap(
                        Map.Entry::getKey,
                        e -> format("%s$$%d", e.getKey().sym.sym, uniqueInt())));

                PVector<LocalVar> closedOverParamOrder = closedOverVars.entrySet().stream().map(Map.Entry::getKey).collect(toPVector());
                PVector<Class<?>> closedOverParamClasses = closedOverParamOrder.stream().map(p -> closedOverVars.get(p).javaType).collect(toPVector());

                for (int i = 0; i < paramTypes.size(); i++) {
                    Type paramType = paramTypes.get(i);
                    Class<?> paramClass = paramType.javaType;

                    paramClasses = paramClasses.plus(paramClass);
                    fnLocals = fnLocals.newVarLocal(expr.params.get(i), paramClass).left;
                }

                for (LocalVar closedOverVar : closedOverParamOrder) {
                    fnLocals = fnLocals.newFieldLocal(closedOverVar, closedOverVars.get(closedOverVar).javaType, fromClassName(className), closedOverFieldNames.get(closedOverVar)).left;
                }

                Instructions bodyInstructions = compileExpr0(fnLocals, expr.body);

                NewClass newClass = newClass(className);

                for (LocalVar closedOverVar : closedOverParamOrder) {
                    newClass = newClass.withField(newField(closedOverFieldNames.get(closedOverVar), closedOverVars.get(closedOverVar).javaType, setOf(PRIVATE, FINAL)));
                }

                Instructions constructorInstructions =
                    mplus(loadThis(), Instructions.OBJECT_SUPER_CONSTRUCTOR_CALL);

                Locals constructorLocals = instanceLocals();

                for (LocalVar localVar : closedOverParamOrder) {
                    Class<?> paramClass = closedOverVars.get(localVar).javaType;
                    Pair<Locals, Locals.Local.VarLocal> paramLocalResult = constructorLocals.newVarLocal(localVar, paramClass);
                    constructorLocals = paramLocalResult.left;
                    constructorInstructions = mplus(constructorInstructions,
                        loadThis(),
                        localVarCall(paramLocalResult.right),
                        fieldOp(PUT_FIELD, fromClassName(className), closedOverFieldNames.get(localVar), fromClass(paramClass)));
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

                    newObject(fromClass(fnClass), closedOverParamClasses,
                        mplus(closedOverParamOrder.stream()
                            .map(p -> localVarCall(locals.locals.get(p)))
                            .collect(toPVector()))),

                    bindMethodHandle());
            }

            @Override
            public Instructions visit(Expr.DefExpr<? extends Type> expr) {
                String className = format("user$$%s$$%d", expr.sym, uniqueInt());
                Type type = expr.body.type;
                Class<?> clazz = type.javaType;

                boolean isFn = expr.body instanceof Expr.FnExpr && type instanceof Type.FnType;

                Expr.FnExpr<? extends Type> fnExpr = null;
                Type.FnType fnType = null;
                PVector<Class<?>> paramTypes = null;
                MethodType fnMethodType = null;

                if (isFn) {
                    fnType = (Type.FnType) type;
                    fnExpr = (Expr.FnExpr<? extends Type>) expr.body;
                    paramTypes = fnType.paramTypes.stream().map(pt -> pt.javaType).collect(toPVector());
                    fnMethodType = MethodType.methodType(
                        fnType.returnType.javaType,
                        paramTypes);
                }

                Instructions valueInstructions = isFn
                    ? Instructions.staticMethodHandle(toInternalName(className), FN_METHOD_NAME, paramTypes, fnMethodType.returnType())
                    : compileExpr0(staticLocals(), expr.body);

                NewClass newClass = newClass(className)
                    .withField(newField(VALUE_FIELD_NAME, clazz, setOf(STATIC, FINAL, PUBLIC)))
                    .withMethod(newMethod(setOf(STATIC), "<clinit>", Void.TYPE, vectorOf(),
                        mplus(valueInstructions,
                            fieldOp(PUT_STATIC, fromClassName(className), VALUE_FIELD_NAME, fromClass(clazz)),
                            ret(Void.TYPE))));

                if (isFn) {
                    Locals locals = staticLocals();
                    for (int i = 0; i < fnExpr.params.size(); i++) {
                        locals = locals.newVarLocal(fnExpr.params.get(i), fnType.paramTypes.get(i).javaType).left;
                    }

                    newClass = newClass.withMethod(newMethod(setOf(STATIC, PUBLIC), FN_METHOD_NAME, fnMethodType.returnType(), paramTypes,
                        mplus(
                            compileExpr0(locals, fnExpr.body),
                            ret(fnType.returnType.javaType))));
                }

                Class<?> dynClass = defineClass(newClass);

                return newObject(fromClass(EnvUpdate.DefEnvUpdate.class), vectorOf(Symbol.class, Type.class, Class.class, MethodType.class),
                    mplus(
                        loadSymbol(expr.sym),
                        loadType(type, locals),
                        loadClass(dynClass),
                        fnMethodType == null
                            ? Instructions.loadNull()
                            : loadMethodType(fnMethodType)));
            }

            @Override
            public Instructions visit(Expr.TypeDefExpr<? extends Type> expr) {
                return newObject(fromClass(EnvUpdate.TypeDefEnvUpdate.class), vectorOf(Symbol.class, Type.class),
                    mplus(
                        loadSymbol(expr.sym),
                        loadType(expr.typeDef, locals)));
            }

            @Override
            public Instructions visit(Expr.DefDataExpr<? extends Type> expr) {
                DataType<? extends Type> dataType = expr.dataType;

                Class<?> superClass = defineClass(newClass(format("user$$%s$$%d", dataType.sym.sym, uniqueInt()), setOf(PUBLIC, ABSTRACT))
                    .withMethod(newMethod(setOf(PUBLIC), "<init>", Void.TYPE, Empty.vector(),
                        mplus(loadThis(), OBJECT_SUPER_CONSTRUCTOR_CALL, ret(Void.TYPE)))));

                Map<Symbol, Class<?>> constructors = new HashMap<>();

                for (DataTypeConstructor constructor : expr.dataType.constructors) {
                    String subclassName = format("user$$%s$$%s$$%d", dataType.sym.sym, constructor.sym.sym, uniqueInt());

                    constructors.put(constructor.sym, defineClass(
                        newClass(subclassName)
                            .withSuperClass(superClass)
                            .withField(newField(VALUE_FIELD_NAME, superClass, setOf(PUBLIC, STATIC, FINAL)))
                            .withMethod(newMethod(setOf(PUBLIC, STATIC), "<clinit>", Void.TYPE, Empty.vector(),
                                mplus(
                                    newObject(fromClassName(subclassName), Empty.vector(), MZERO),
                                    fieldOp(PUT_STATIC, fromClassName(subclassName), VALUE_FIELD_NAME, fromClass(superClass)),
                                    ret(Void.TYPE)
                                )))
                            .withMethod(newMethod(setOf(PUBLIC), "<init>", Void.TYPE, Empty.vector(),
                                mplus(loadThis(), methodCall(fromClass(superClass), INVOKE_SPECIAL, "<init>", Void.TYPE, Empty.vector()), ret(Void.TYPE))))));
                }

                return newObject(fromClass(EnvUpdate.DefDataEnvUpdate.class), vectorOf(DataType.class, Class.class, PMap.class),
                    mplus(
                        loadDataType(dataType, locals),
                        loadClass(superClass),
                        loadMap(constructors.entrySet().stream().map(e -> mplus(loadSymbol(e.getKey()), loadClass(e.getValue()))).collect(toPVector()))));
            }
        });
    }

    public static EvalResult compile(Env env, Expr<? extends Type> expr) {
        Instructions instructions = compileExpr0(staticLocals(), expr);
        Class<?> returnType = expr.type.javaType;

        try {
            Object result = publicLookup()
                .findStatic(
                    defineClass(
                        newClass("user$$eval$$" + uniqueInt())
                            .withMethod(
                                newMethod(setOf(PUBLIC, FINAL, STATIC), "$$eval", Object.class, vectorOf(),
                                    mplus(
                                        instructions,
                                        box(org.objectweb.asm.Type.getType(returnType)),
                                        ret(Object.class))))),


                    "$$eval",
                    MethodType.methodType(Object.class))
                .invoke();

            if (result instanceof EnvUpdate) {
                Pair<Env, ?> envUpdateResult = ((EnvUpdate<?>) result).updateEnv(env);
                return new EvalResult(envUpdateResult.left, envUpdateResult.right);
            } else {
                return new EvalResult(env, result);
            }
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
}
