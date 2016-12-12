package bridje.compiler;

import bridje.runtime.*;
import bridje.types.Type;
import bridje.util.Pair;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static bridje.runtime.Var.FN_METHOD_NAME;
import static bridje.runtime.Var.VALUE_FIELD_NAME;
import static bridje.util.Pair.pair;

public interface EnvUpdate<T> {
    Pair<Env, T> updateEnv(Env env);

    class NSEnvUpdate implements EnvUpdate<NS> {

        final NS ns;
        final PMap<Symbol, NS> aliases;
        final PMap<Symbol, FQSymbol> refers;

        public NSEnvUpdate(NS ns, PMap<Symbol, NS> aliases, PMap<Symbol, FQSymbol> refers) {
            this.ns = ns;
            this.aliases = aliases;
            this.refers = refers;
        }

        @Override
        public Pair<Env, NS> updateEnv(Env env) {
            return pair(env.withNS(ns, aliases, refers), ns);
        }
    }

    class DefEnvUpdate implements EnvUpdate<Var> {

        private final FQSymbol sym;
        private final Type type;

        private final Class<?> dynClass;
        private final MethodType fnMethodType;

        public DefEnvUpdate(FQSymbol sym, Type type, Class<?> dynClass, MethodType fnMethodType) {
            this.sym = sym;
            this.type = type;
            this.dynClass = dynClass;
            this.fnMethodType = fnMethodType;
        }

        @Override
        public Pair<Env, Var> updateEnv(Env env) {
            Field valueField;
            Method fnMethod = null;

            try {
                valueField = dynClass.getField(VALUE_FIELD_NAME);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }


            if (fnMethodType != null) {
                try {
                    fnMethod = dynClass.getMethod(FN_METHOD_NAME, fnMethodType.parameterArray());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }

            Var oldVar = env.vars.get(sym);
            Type declaredType = null;
            if (oldVar != null && (declaredType = oldVar.declaredType) != null) {
                if (!declaredType.subtypeOf(type)) {
                    throw new UnsupportedOperationException();
                }
            }

            Var var = new Var(declaredType, type, valueField, fnMethod);
            return pair(env.withVar(sym, var), var);
        }
    }

    class TypeDefEnvUpdate implements EnvUpdate<Var> {

        private final FQSymbol sym;
        private final Type typeDef;

        public TypeDefEnvUpdate(FQSymbol sym, Type typeDef) {
            this.sym = sym;
            this.typeDef = typeDef;
        }

        @Override
        public Pair<Env, Var> updateEnv(Env env) {
            Var var = new Var(typeDef, null, null, null);
            return pair(env.withVar(sym, var), var);
        }
    }

    class DefDataEnvUpdate implements EnvUpdate {

        private final DataType<Type> dataType;
        private final Class<?> superClass;
        private final PMap<FQSymbol, Class<?>> constructorClasses;

        public DefDataEnvUpdate(DataType<Type> dataType, Class<?> superClass, PMap<FQSymbol, Class<?>> constructorClasses) {
            this.dataType = dataType;
            this.superClass = superClass;
            this.constructorClasses = constructorClasses;
        }

        @Override
        public Pair<Env, ?> updateEnv(Env env) {
            Map<FQSymbol, Var> vars = new HashMap<>();

            for (DataTypeConstructor<Type> constructor : dataType.constructors) {
                FQSymbol constructorSym = constructor.sym;
                Class<?> clazz = constructorClasses.get(constructorSym);

                Field field;
                try {
                    field = clazz.getDeclaredField(VALUE_FIELD_NAME);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }

                vars.put(constructorSym, new Var(constructor.type, constructor.type, field, null));
            }

            return pair(
                env.withDataType(
                    dataType,
                    superClass,
                    HashTreePMap.from(vars)),
                dataType);
        }
    }
}
