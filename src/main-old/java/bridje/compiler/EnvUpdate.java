package bridje.compiler;

import bridje.runtime.Env;
import bridje.runtime.EvalResult;
import bridje.util.Pair;

public interface EnvUpdate<T> {
    EvalResult<T> updateEnv(Env env);

//    class NSEnvUpdate implements EnvUpdate<NS> {
//
//        final NS ns;
//        final PMap<Symbol, NS> aliases;
//        final PMap<Symbol, FQSymbol> refers;
//        final PMap<Symbol, Class<?>> imports;
//
//        public NSEnvUpdate(NS ns, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers, PMap<Symbol, Class<?>> imports) {
//            this.ns = ns;
//            this.aliases = aliases;
//            this.refers = refers;
//            this.imports = imports;
//        }
//
//        @Override
//        public Pair<Env, NS> updateEnv(Env env) {
//            return pair(env.withNS(ns, aliases, refers, imports), ns);
//        }
//    }
//
//    class DefEnvUpdate implements EnvUpdate<Var> {
//
//        private final FQSymbol sym;
//        private final Type type;
//
//        private final Class<?> dynClass;
//        private final MethodType fnMethodType;
//
//        public DefEnvUpdate(FQSymbol sym, Type type, Class<?> dynClass, MethodType fnMethodType) {
//            this.sym = sym;
//            this.type = type;
//            this.dynClass = dynClass;
//            this.fnMethodType = fnMethodType;
//        }
//
//        @Override
//        public Pair<Env, Var> updateEnv(Env env) {
//            Field valueField;
//            Method fnMethod = null;
//
//            try {
//                valueField = dynClass.getField(VALUE_FIELD_NAME);
//            } catch (NoSuchFieldException e) {
//                throw new RuntimeException(e);
//            }
//
//
//            if (fnMethodType != null) {
//                try {
//                    fnMethod = dynClass.getMethod(FN_METHOD_NAME, fnMethodType.parameterArray());
//                } catch (NoSuchMethodException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//
//            Var oldVar = env.vars.get(sym);
//            Type declaredType = null;
//            if (oldVar != null && (declaredType = oldVar.declaredType) != null) {
//                if (!declaredType.subtypeOf(type)) {
//                    throw new UnsupportedOperationException();
//                }
//            }
//
//            Var var = new Var(type, valueField, fnMethod);
//            return pair(env.withVar(sym, var), var);
//        }
//    }
//
//    class DefDataEnvUpdate implements EnvUpdate {
//
//        private final DataType<Type> dataType;
//        private final Class<?> superClass;
//        private final PMap<FQSymbol, Class<?>> constructorClasses;
//
//        public DefDataEnvUpdate(DataType<Type> dataType, Class<?> superClass, PMap<FQSymbol, Class<?>> constructorClasses) {
//            this.dataType = dataType;
//            this.superClass = superClass;
//            this.constructorClasses = constructorClasses;
//        }
//
//        @Override
//        public Pair<Env, ?> updateEnv(Env env) {
//            Map<FQSymbol, Var> vars = new HashMap<>();
//
//            for (DataTypeConstructor<Type> constructor : dataType.constructors) {
//                FQSymbol constructorSym = constructor.sym;
//                Class<?> clazz = constructorClasses.get(constructorSym);
//
//                Field field;
//                try {
//                    field = clazz.getDeclaredField(VALUE_FIELD_NAME);
//                } catch (NoSuchFieldException e) {
//                    throw new RuntimeException(e);
//                }
//
//                vars.put(constructorSym, new Var(constructor.type, field, null));
//            }
//
//            return pair(
//                env.withDataType(
//                    dataType,
//                    superClass,
//                    HashTreePMap.from(vars)),
//                dataType);
//        }
//    }
}
