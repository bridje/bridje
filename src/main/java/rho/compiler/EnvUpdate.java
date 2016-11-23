package rho.compiler;

import rho.runtime.Env;
import rho.runtime.Var;
import rho.util.Pair;

public interface EnvUpdate<T> {
    Pair<Env, T> updateEnv(Env env);

    class DefEnvUpdate implements EnvUpdate<Var> {

        @Override
        public Pair<Env, Var> updateEnv(Env env) {
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
//            if (isFn) {
//                try {
//                    fnMethod = dynClass.getMethod(FN_METHOD_NAME, fnMethodType.parameterArray());
//                } catch (NoSuchMethodException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//
//            Var var = new Var(null, type, valueField, fnMethod);
//            return new EvalResult(env.withVar(sym, var), var);

            throw new UnsupportedOperationException();
        }
    }

    class TypeDefEnvUpdate implements EnvUpdate<Var> {
        @Override
        public Pair<Env, Var> updateEnv(Env env) {
//            Var var = new Var(expr.typeDef, null, null, null);
//            return new EvalResult(env.withVar(expr.sym, var), var);
            throw new UnsupportedOperationException();
        }
    }

}
