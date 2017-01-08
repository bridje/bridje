package bridje.compiler;

import bridje.Panic;
import bridje.analyser.Expr;
import bridje.analyser.ExprVisitor;
import bridje.analyser.LocalVar;
import bridje.runtime.Env;
import bridje.runtime.EvalResult;
import bridje.runtime.JCall;
import bridje.util.Pair;
import org.objectweb.asm.Type;
import org.pcollections.*;

import java.lang.reflect.InvocationTargetException;

import static bridje.Panic.panic;
import static bridje.Util.*;
import static bridje.compiler.AccessFlag.*;
import static bridje.compiler.ClassDefiner.defineClass;
import static bridje.compiler.ClassLike.fromClass;
import static bridje.compiler.Instructions.FieldOp.GET_STATIC;
import static bridje.compiler.Instructions.*;
import static bridje.compiler.Locals.staticLocals;
import static bridje.compiler.NewClass.newClass;
import static bridje.compiler.NewMethod.newMethod;
import static bridje.runtime.MethodInvoke.INVOKE_STATIC;
import static bridje.runtime.MethodInvoke.INVOKE_VIRTUAL;
import static bridje.util.Pair.pair;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

public class Compiler {

    static PSet<LocalVar> closedOverVars(PSet<LocalVar> boundVars, Expr expr) {
        return expr.accept(new ExprVisitor<PSet<LocalVar>>() {

            private PSet<LocalVar> mapClosedOverVars(PCollection<? extends Expr> exprs) {
                return exprs.stream().flatMap(e -> e.accept(this).stream())
                    .collect(toPSet());
            }

            @Override
            public PSet<LocalVar> visit(Expr.BoolExpr expr) {
                return Empty.set();
            }

            @Override
            public PSet<LocalVar> visit(Expr.StringExpr expr) {
                return Empty.set();
            }

            @Override
            public PSet<LocalVar> visit(Expr.IntExpr expr) {
                return Empty.set();
            }

            @Override
            public PSet<LocalVar> visit(Expr.VectorExpr expr) {
                return mapClosedOverVars(expr.exprs);
            }

            @Override
            public PSet<LocalVar> visit(Expr.SetExpr expr) {
                return mapClosedOverVars(expr.exprs);
            }

            @Override
            public PSet<LocalVar> visit(Expr.MapExpr expr) {
                return mapClosedOverVars(expr.entries.stream().flatMap(e -> vectorOf(e.key, e.value).stream()).collect(toPVector()));
            }

            @Override
            public PSet<LocalVar> visit(Expr.CallExpr expr) {
                return mapClosedOverVars(expr.exprs);
            }

            @Override
            public PSet<LocalVar> visit(Expr.VarCallExpr expr) {
                return mapClosedOverVars(expr.params);
            }

            @Override
            public PSet<LocalVar> visit(Expr.LetExpr expr) {
                PSet<LocalVar> boundVars_ = boundVars;
                PSet<LocalVar> result = Empty.set();

                for (Expr.LetExpr.LetBinding binding : expr.bindings) {
                    result = result.plusAll(closedOverVars(boundVars_, binding.expr));
                    boundVars_ = boundVars_.plus(binding.localVar);
                }

                return closedOverVars(boundVars_, expr.body);
            }

            @Override
            public PSet<LocalVar> visit(Expr.IfExpr expr) {
                return expr.testExpr.accept(this)
                    .plusAll(expr.thenExpr.accept(this))
                    .plusAll(expr.elseExpr.accept(this));
            }

            @Override
            public PSet<LocalVar> visit(Expr.LocalVarExpr expr) {
                if (!boundVars.contains(expr.localVar)) {
                    return HashTreePSet.singleton(expr.localVar);
                } else {
                    return Empty.set();
                }
            }

            @Override
            public PSet<LocalVar> visit(Expr.GlobalVarExpr expr) {
                return Empty.set();
            }

            @Override
            public PSet<LocalVar> visit(Expr.FnExpr expr) {
                return closedOverVars(boundVars.plusAll(expr.params), expr.body);
            }

            @Override
            public PSet<LocalVar> visit(Expr.NSExpr expr) {
                return Empty.set();
            }

            @Override
            public PSet<LocalVar> visit(Expr.DefExpr expr) {
                return expr.body.accept(this);
            }

            @Override
            public PSet<LocalVar> visit(Expr.DefJExpr expr) {
                return Empty.set();
            }

            @Override
            public PSet<LocalVar> visit(Expr.DefDataExpr expr) {
                return Empty.set();
            }
        });
    }

    private static Instructions compileExpr0(Locals locals, Expr expr, Type returnType) {
        return expr.accept(new ExprVisitor<Instructions>() {
            @Override
            public Instructions visit(Expr.BoolExpr expr) {
                return mplus(loadBool(expr.value), coerce(Type.BOOLEAN_TYPE, returnType));
            }

            @Override
            public Instructions visit(Expr.StringExpr expr) {
                return mplus(loadObject(expr.string), coerce(Type.getType(String.class), returnType));
            }

            @Override
            public Instructions visit(Expr.IntExpr expr) {
                return mplus(loadObject(expr.num), coerce(Type.LONG_TYPE, returnType));
            }

            @Override
            public Instructions visit(Expr.VectorExpr expr) {
                return Instructions.loadVector(fmap(expr.exprs, e -> compileExpr0(locals, e, OBJECT_TYPE)));
            }

            @Override
            public Instructions visit(Expr.SetExpr expr) {
                return Instructions.loadSet(fmap(expr.exprs, e -> compileExpr0(locals, e, OBJECT_TYPE)));
            }

            @Override
            public Instructions visit(Expr.MapExpr expr) {
                return Instructions.loadMap(fmap(expr.entries, e -> pair(compileExpr0(locals, e.key, OBJECT_TYPE), compileExpr0(locals, e.value, OBJECT_TYPE))));
            }

            @Override
            public Instructions visit(Expr.CallExpr expr) {
//                PVector<? extends Expr> params = expr.exprs;
//
//                Expr fn = params.get(0);
//                PVector<? extends Expr> args = expr.exprs.minus(0);
//
//                Instructions fnInstructions = compileExpr0(locals, fn);
//
//                PVector<Instructions> paramInstructions = args.stream().map(p -> compileExpr0(locals, p)).collect(toPVector());
//
//                return mplus(
//                    fnInstructions,
//                    mplus(paramInstructions),
//                    methodCall(fromClass(MethodHandle.class), MethodInvoke.INVOKE_VIRTUAL, "invoke", expr.type.javaType, args.stream().map(a -> a.type.javaType).collect(toPVector())));
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(Expr.VarCallExpr expr) {
//                return varCall(expr.var, expr.params.stream().map(p -> compileExpr0(locals, p)).collect(toPVector()));
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(Expr.LetExpr expr) {
//                List<Instructions> bindingsInstructions = new LinkedList<>();
//                Locals locals_ = locals;
//
//                for (Expr.LetExpr.LetBinding binding : expr.bindings) {
//                    Instructions bindingInstructions = compileExpr0(locals_, binding.expr);
//
//                    locals_ = locals_.withVarLocal(binding.localVar, binding.expr.type.javaType);
//
//                    bindingsInstructions.add(locals_.get(binding.localVar).store(bindingInstructions));
//                }
//
//                Instructions bodyInstructions = compileExpr0(locals_, expr.body);
//
//                return mplus(mplus(TreePVector.from(bindingsInstructions)), bodyInstructions);
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(Expr.IfExpr expr) {
                return ifCall(compileExpr0(locals, expr.testExpr, Type.BOOLEAN_TYPE), compileExpr0(locals, expr.thenExpr, returnType), compileExpr0(locals, expr.elseExpr, returnType));
            }

            @Override
            public Instructions visit(Expr.LocalVarExpr expr) {
                return locals.get(expr.localVar).load();
            }

            @Override
            public Instructions visit(Expr.GlobalVarExpr expr) {
                return globalVarValue(expr.var);
            }

            @Override
            public Instructions visit(Expr.FnExpr expr) {
//                Type.FnType fnType = (Type.FnType) expr.type;
//                String className = "user$$fn$$" + uniqueInt();
//
//                PVector<Type> paramTypes = fnType.paramTypes;
//                PVector<Class<?>> paramClasses = Empty.vector();
//                Class<?> returnClass = fnType.returnType.javaType;
//                Locals fnLocals = instanceLocals();
//
//                PMap<LocalVar, Type> closedOverVars = closedOverVars(HashTreePSet.from(expr.params), expr.body);
//
//                PMap<LocalVar, String> closedOverFieldNames = closedOverVars.entrySet().stream().collect(
//                    toPMap(
//                        Map.Entry::getKey,
//                        e -> format("%s$$%d", e.getKey().sym.sym, uniqueInt())));
//
//                PVector<LocalVar> closedOverParamOrder = closedOverVars.entrySet().stream().map(Map.Entry::getKey).collect(toPVector());
//                PVector<Class<?>> closedOverParamClasses = closedOverParamOrder.stream().map(p -> closedOverVars.get(p).javaType).collect(toPVector());
//
//                for (int i = 0; i < paramTypes.size(); i++) {
//                    Type paramType = paramTypes.get(i);
//                    Class<?> paramClass = paramType.javaType;
//
//                    paramClasses = paramClasses.plus(paramClass);
//                    fnLocals = fnLocals.withVarLocal(expr.params.get(i), paramClass);
//                }
//
//                for (LocalVar closedOverVar : closedOverParamOrder) {
//                    fnLocals = fnLocals.withFieldLocal(closedOverVar, fromClassName(className), closedOverFieldNames.get(closedOverVar), fromClass(closedOverVars.get(closedOverVar).javaType));
//                }
//
//                Instructions bodyInstructions = compileExpr0(fnLocals, expr.body);
//
//                NewClass newClass = newClass(className);
//
//                for (LocalVar closedOverVar : closedOverParamOrder) {
//                    newClass = newClass.withField(newField(closedOverFieldNames.get(closedOverVar), closedOverVars.get(closedOverVar).javaType, setOf(PRIVATE, FINAL)));
//                }
//
//                Instructions constructorInstructions =
//                    mplus(loadThis(), Instructions.OBJECT_SUPER_CONSTRUCTOR_CALL);
//
//                Locals constructorLocals = instanceLocals();
//
//                for (LocalVar localVar : closedOverParamOrder) {
//                    Class<?> paramClass = closedOverVars.get(localVar).javaType;
//                    constructorLocals = constructorLocals.withVarLocal(localVar, paramClass);
//                    constructorInstructions = mplus(constructorInstructions,
//                        loadThis(),
//                        constructorLocals.get(localVar).load(),
//                        fieldOp(PUT_FIELD, fromClassName(className), closedOverFieldNames.get(localVar), fromClass(paramClass)));
//                }
//
//                Class<?> fnClass = defineClass(newClass
//                    .withMethod(newMethod(setOf(PUBLIC), "<init>", Void.TYPE, closedOverParamClasses,
//                        mplus(constructorInstructions, ret(Void.TYPE))))
//                    .withMethod(newMethod(setOf(PUBLIC, FINAL), "$$fn",
//                        returnClass,
//                        paramClasses,
//                        mplus(
//                            bodyInstructions,
//                            ret(returnClass)))));
//
//
//                return mplus(
//                    virtualMethodHandle(fnClass, "$$fn", paramClasses, returnClass),
//
//                    newObject(fromClass(fnClass), closedOverParamClasses,
//                        mplus(closedOverParamOrder.stream()
//                            .map(p -> locals.get(p).load())
//                            .collect(toPVector()))),
//
//                    bindMethodHandle());
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(Expr.NSExpr expr) {
//                return newObject(fromClass(EnvUpdate.NSEnvUpdate.class), vectorOf(NS.class, PMap.class, PMap.class, PMap.class),
//                    mplus(
//                        loadNS(expr.ns),
//                        loadMap(Symbol.class, NS.class,
//                            expr.aliases.entrySet().stream().map(e -> pair(loadSymbol(e.getKey()), loadNS(e.getValue()))).collect(toPVector())),
//                        loadMap(Symbol.class, FQSymbol.class,
//                            expr.refers.entrySet().stream().map(e -> pair(loadSymbol(e.getKey()), loadFQSymbol(e.getValue()))).collect(toPVector())),
//                        loadMap(Symbol.class, Class.class,
//                            expr.imports.entrySet().stream().map(e -> pair(loadSymbol(e.getKey()), loadClass(e.getValue()))).collect(toPVector()))));
                throw new UnsupportedOperationException();
            }

//            private Instructions compileDefValue(FQSymbol fqSym, Type type, NewClass newClass, Instructions valueInstructions) {
//                Class<?> clazz = type.javaType;
//
//                newClass = newClass
//                    .withField(newField(VALUE_FIELD_NAME, clazz, setOf(STATIC, FINAL, PUBLIC)))
//                    .withMethod(newMethod(setOf(STATIC), "<clinit>", Void.TYPE, vectorOf(),
//                        mplus(
//                            valueInstructions,
//                            fieldOp(PUT_STATIC, fromClassName(newClass.name), VALUE_FIELD_NAME, fromClass(clazz)),
//                            ret(Void.TYPE))));
//
//                Class<?> dynClass = defineClass(newClass);
//
//                return newObject(fromClass(EnvUpdate.DefEnvUpdate.class), vectorOf(FQSymbol.class, Type.class, Class.class, MethodType.class),
//                    mplus(
//                        loadFQSymbol(fqSym),
//                        withTypeLocals(locals, type.typeVars(), typeLocals -> loadType(type, typeLocals)),
//                        loadClass(dynClass),
//                        Instructions.loadNull()));
//            }
//
//            private Instructions compileDefFn(FQSymbol fqSym, Type.FnType fnType, NewClass newClass, Instructions fnInstructions) {
//                PVector<Class<?>> paramTypes = fnType.paramTypes.stream().map(pt -> pt.javaType).collect(toPVector());
//                MethodType fnMethodType = methodType(fnType.returnType.javaType, paramTypes);
//
//                Class<?> clazz = fnType.javaType;
//
//                newClass = newClass
//                    .withField(newField(VALUE_FIELD_NAME, clazz, setOf(STATIC, FINAL, PUBLIC)))
//                    .withMethod(newMethod(setOf(STATIC), "<clinit>", Void.TYPE, vectorOf(),
//                        mplus(Instructions.staticMethodHandle(fromClassName(newClass.name), FN_METHOD_NAME, paramTypes, fnMethodType.returnType()),
//                            fieldOp(PUT_STATIC, fromClassName(newClass.name), VALUE_FIELD_NAME, fromClass(clazz)),
//                            ret(Void.TYPE))))
//
//                    .withMethod(newMethod(setOf(STATIC, PUBLIC), FN_METHOD_NAME, fnMethodType.returnType(), paramTypes, fnInstructions));
//
//                Class<?> dynClass = defineClass(newClass);
//
//                return newObject(fromClass(EnvUpdate.DefEnvUpdate.class), vectorOf(FQSymbol.class, Type.class, Class.class, MethodType.class),
//                    mplus(
//                        loadFQSymbol(fqSym),
//                        withTypeLocals(locals, fnType.typeVars(), typeLocals -> loadType(fnType, typeLocals)),
//                        loadClass(dynClass),
//                        loadMethodType(fnMethodType)));
//            }

            @Override
            public Instructions visit(Expr.DefExpr expr) {
//                Type type = expr.body.type;
//
//                String className = format("%s$$%s$$%d", expr.sym.ns.name, expr.sym.symbol, uniqueInt());
//                NewClass newClass = newClass(className);
//
//                if (expr.body instanceof Expr.FnExpr && type instanceof Type.FnType) {
//                    Type.FnType fnType = (Type.FnType) type;
//                    Expr.FnExpr fnExpr = (Expr.FnExpr) expr.body;
//
//                    Locals locals = staticLocals();
//                    for (int i = 0; i < fnExpr.params.size(); i++) {
//                        locals = locals.withVarLocal(fnExpr.params.get(i), fnType.paramTypes.get(i).javaType);
//                    }
//
//                    Instructions fnInstructions = mplus(
//                        compileExpr0(locals, fnExpr.body),
//                        ret(fnType.returnType.javaType));
//
//                    return compileDefFn(expr.sym, fnType, newClass, fnInstructions);
//                } else {
//                    return compileDefValue(expr.sym, type, newClass, compileExpr0(staticLocals(), expr.body));
//                }
                throw new UnsupportedOperationException();
            }

            private Instructions callInstructions(JCall call, Instructions paramInstructions) {
                return call.accept(new JCall.JCallVisitor<Instructions>() {
                    @Override
                    public Instructions visit(JCall.StaticMethodCall call) {
                        return mplus(
                            paramInstructions,
                            methodCall(fromClass(call.clazz), INVOKE_STATIC, call.name,
                                call.signature.jReturn.returnClass,
                                call.signature.jParams.stream().map(p -> p.paramClass).collect(toPVector())));
                    }

                    @Override
                    public Instructions visit(JCall.InstanceMethodCall call) {
                        return mplus(paramInstructions,
                            methodCall(fromClass(call.clazz), INVOKE_VIRTUAL, call.name,
                                call.signature.jReturn.returnClass,
                                call.signature.jParams.stream().map(p -> p.paramClass).collect(toPVector())));
                    }

                    @Override
                    public Instructions visit(JCall.ConstructorCall call) {
                        return newObject(fromClass(call.clazz),
                            call.signature.jParams.stream().map(p -> p.paramClass).collect(toPVector()),
                            paramInstructions);
                    }

                    @Override
                    public Instructions visit(JCall.GetStaticFieldCall call) {
                        return fieldOp(GET_STATIC, fromClass(call.clazz), call.name, fromClass(call.signature.jReturn.returnClass));
                    }
                });
            }

            private Locals withParams(Locals locals, PVector<Class<?>> classes) {
                for (int i = 0; i < classes.size(); i++) {
                    locals = locals.withVarLocal(i, classes.get(i));
                }

                return locals;
            }

            @Override
            public Instructions visit(Expr.DefJExpr expr) {
//                Type typeDef = expr.typeDef;
//
//                NewClass newClass = newClass(format("%s$$%s$$%d", expr.sym.ns.name, expr.sym.symbol.sym, uniqueInt()));
//                ClassLike classLike = fromClassName(newClass.name);
//
//                JCall jCall = expr.jCall;
//                JSignature sig = jCall.signature;
//                PVector<ReturnWrapper> returnWrappers = sig.jReturn.wrappers;
//
//
//                boolean isIO = !returnWrappers.isEmpty() && returnWrappers.get(0) == ReturnWrapper.IO;
//                boolean isFn = typeDef instanceof Type.FnType;
//
//                if (isIO) {
//                    returnWrappers = returnWrappers.minus(0);
//                }
//
//                Type.FnType fnType = isFn ? ((Type.FnType) typeDef) : null;
//                Type returnType = fnType != null ? fnType.returnType : typeDef;
//                PVector<Type> paramTypes = isFn ? fnType.paramTypes : Empty.vector();
//                PVector<Class<?>> paramClasses = paramTypes.stream().map(p -> p.javaType).collect(toPVector());
//                int paramCount = paramTypes.size();
//
//                Locals staticParamLocals = withParams(staticLocals(), paramClasses);
//                // TODO wrap these with param wrappers
//                Instructions loadParamInstructions = mplus(IntStream.range(0, paramCount).mapToObj(i -> staticParamLocals.get(i).load()).collect(toPVector()));
//
//                // TODO wrap this with other return wrappers
//                Instructions callInstructions = callInstructions(jCall, loadParamInstructions);
//
//                if (isIO) {
//                    for (int i = 0; i < paramCount; i++) {
//                        newClass = newClass.withField(newField("param$" + i, paramTypes.get(i).javaType, setOf(PRIVATE, FINAL)));
//                    }
//
//                    Locals instanceParamLocals = withParams(instanceLocals(), paramClasses);
//
//                    newClass = newClass
//                        .withInterface(IO.class)
//                        .withMethod(newMethod(Empty.set(), "<init>", Void.TYPE, paramClasses,
//                            mplus(
//                                loadThis(), OBJECT_SUPER_CONSTRUCTOR_CALL,
//                                mplus(IntStream.range(0, paramCount)
//                                    .mapToObj(i -> mplus(
//                                        loadThis(),
//                                        instanceParamLocals.get(i).load(),
//                                        fieldOp(PUT_FIELD, classLike, "param$" + i, fromClass(paramTypes.get(i).javaType))))
//                                    .collect(toPVector())),
//                                ret(Void.TYPE))))
//
//                        .withMethod(newMethod(setOf(PUBLIC, FINAL), "runIO", Object.class, Empty.vector(),
//                            mplus(
//                                callInstructions(jCall,
//                                    mplus(IntStream.range(0, paramCount)
//                                        .mapToObj(i -> mplus(
//                                            loadThis(),
//                                            fieldOp(GET_FIELD, classLike, "param$" + i, fromClass(paramTypes.get(i).javaType))))
//                                        .collect(toPVector()))),
//                                box(returnType.javaType),
//                                ret(Object.class))));
//
//                    callInstructions = newObject(classLike, paramClasses, loadParamInstructions);
//                }
//
//                if (isFn) {
//                    return compileDefFn(expr.sym, fnType, newClass, mplus(callInstructions, ret(returnType.javaType)));
//                } else {
//                    return compileDefValue(expr.sym, typeDef, newClass, callInstructions);
//                }
                throw new UnsupportedOperationException();
            }

            @Override
            public Instructions visit(Expr.DefDataExpr expr) {
//                DataType<? extends Type> dataType = expr.dataType;
//                Type type = dataType.type;
//                if (type instanceof Type.AppliedType) {
//                    type = ((Type.AppliedType) type).appliedType;
//                }
//
//                Class<?> superClass = defineClass(newClass(format("%s$$%s$$%d", dataType.sym.ns.name, dataType.sym.symbol, uniqueInt()), setOf(PUBLIC, ABSTRACT))
//                    .withMethod(newMethod(setOf(PUBLIC), "<init>", Void.TYPE, Empty.vector(),
//                        mplus(loadThis(), OBJECT_SUPER_CONSTRUCTOR_CALL, ret(Void.TYPE)))));
//
//                Map<FQSymbol, Class<?>> constructors = new HashMap<>();
//                PMap<Type, Class<?>> classMapping = HashTreePMap.singleton(type, superClass);
//
//                dataType = dataType
//                    .fmapType(t -> t.applyJavaTypeMapping(classMapping))
//                    .fmapParamTypes(t -> t.applyJavaTypeMapping(classMapping));
//
//                for (DataTypeConstructor<? extends Type> constructor : dataType.constructors) {
//                    String subclassName = format("%s$$%s$$%s$$%d", dataType.sym.ns.name, dataType.sym.symbol, constructor.sym.symbol, uniqueInt());
//
//                    NewClass newClass = newClass(subclassName).withSuperClass(superClass);
//                    final NewClass baseNewClass = newClass;
//
//                    newClass = constructor.accept(new ConstructorVisitor<NewClass>() {
//                        @Override
//                        public NewClass visit(DataTypeConstructor.ValueConstructor constructor) {
//                            return baseNewClass
//                                .withField(newField(VALUE_FIELD_NAME, superClass, setOf(PUBLIC, STATIC, FINAL)))
//                                .withMethod(newMethod(setOf(PUBLIC), "<init>", Void.TYPE, Empty.vector(),
//                                    mplus(
//                                        loadThis(),
//                                        methodCall(fromClass(superClass), INVOKE_SPECIAL, "<init>", Void.TYPE, Empty.vector()),
//                                        ret(Void.TYPE))))
//
//                                .withMethod(newMethod(setOf(PUBLIC, STATIC), "<clinit>", Void.TYPE, Empty.vector(),
//                                    mplus(
//                                        newObject(fromClassName(subclassName), Empty.vector(), MZERO),
//                                        fieldOp(PUT_STATIC, fromClassName(subclassName), VALUE_FIELD_NAME, fromClass(superClass)),
//                                        ret(Void.TYPE))));
//                        }
//
//                        @Override
//                        public NewClass visit(DataTypeConstructor.VectorConstructor constructor) {
//                            NewClass newClass = baseNewClass;
//
//                            PVector<Class<?>> paramClasses = constructor.paramTypes.stream().map(pt -> pt.javaType).collect(toPVector());
//
//                            Instructions setFieldsInstructions = MZERO;
//                            Instructions loadLocalInstructions = MZERO;
//                            Locals setFieldsLocals = instanceLocals();
//                            Locals loadLocalLocals = staticLocals();
//
//                            for (int i = 0; i < constructor.paramTypes.size(); i++) {
//                                Class<?> paramClass = paramClasses.get(i);
//                                String fieldName = "field$$" + i;
//
//                                LocalVar localVar = new LocalVar(symbol(fieldName));
//                                setFieldsLocals = setFieldsLocals.withVarLocal(localVar, paramClass);
//                                setFieldsInstructions = mplus(setFieldsInstructions,
//                                    loadThis(),
//                                    setFieldsLocals.get(localVar).load(),
//                                    fieldOp(PUT_FIELD, fromClassName(subclassName), fieldName, fromClass(paramClass)));
//
//                                loadLocalLocals = loadLocalLocals.withVarLocal(localVar, paramClass);
//                                loadLocalInstructions = mplus(loadLocalInstructions, loadLocalLocals.get(localVar).load());
//
//                                newClass = newClass.withField(newField(fieldName, paramClass, setOf(PUBLIC, FINAL)));
//                            }
//
//                            newClass = newClass
//                                .withField(newField(VALUE_FIELD_NAME, MethodHandle.class, setOf(PUBLIC, STATIC, FINAL)))
//
//                                .withMethod(newMethod(setOf(PUBLIC, STATIC), "<clinit>", Void.TYPE, Empty.vector(),
//                                    mplus(
//                                        staticMethodHandle(fromClassName(subclassName), FN_METHOD_NAME, paramClasses, superClass),
//                                        fieldOp(PUT_STATIC, fromClassName(subclassName), VALUE_FIELD_NAME, fromClass(MethodHandle.class)),
//                                        ret(Void.TYPE))))
//
//                                .withMethod(newMethod(setOf(PUBLIC), "<init>", Void.TYPE, paramClasses,
//                                    mplus(
//                                        loadThis(),
//                                        methodCall(fromClass(superClass), INVOKE_SPECIAL, "<init>", Void.TYPE, Empty.vector()),
//                                        setFieldsInstructions,
//                                        ret(Void.TYPE))))
//
//                                .withMethod(newMethod(setOf(PUBLIC, STATIC), FN_METHOD_NAME, superClass, paramClasses,
//                                    mplus(
//                                        newObject(fromClassName(subclassName), paramClasses, loadLocalInstructions),
//                                        ret(superClass))));
//
//                            return newClass;
//                        }
//                    });
//
//                    constructors.put(constructor.sym, defineClass(newClass));
//                }
//
//                return newObject(fromClass(EnvUpdate.DefDataEnvUpdate.class), vectorOf(DataType.class, Class.class, PMap.class),
//                    mplus(
//                        loadDataType(dataType),
//                        loadClass(superClass),
//                        loadMap(FQSymbol.class, Class.class, constructors.entrySet().stream().map(e -> pair(loadFQSymbol(e.getKey()), loadClass(e.getValue()))).collect(toPVector()))));
                throw new UnsupportedOperationException();
            }
        });
    }

    public static EvalResult compile(Env env, Expr expr) {
        Instructions instructions = compileExpr0(staticLocals(), expr, OBJECT_TYPE);

        try {
            Object result = publicLookup()
                .findStatic(
                    defineClass(
                        newClass("user$$eval$$" + uniqueInt())
                            .withMethod(
                                newMethod(setOf(PUBLIC, FINAL, STATIC), "$$eval", Object.class, vectorOf(),
                                    mplus(
                                        instructions,
                                        ret(OBJECT_TYPE))))),


                    "$$eval",
                    methodType(Object.class))
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
