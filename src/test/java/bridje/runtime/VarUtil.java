package bridje.runtime;

import bridje.types.Type;
import org.pcollections.Empty;
import org.pcollections.HashTreePMap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static bridje.Util.vectorOf;
import static bridje.runtime.Env.EMPTY;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.USER;
import static bridje.runtime.NS.ns;
import static bridje.runtime.Symbol.symbol;
import static bridje.types.Type.FnType.fnType;
import static bridje.types.Type.SimpleType.INT_TYPE;

public class VarUtil {

    public static long plusFn(long a, long b) {
        return a + b;
    }

    public static final MethodHandle PLUS_HANDLE;
    public static final Field PLUS_VALUE_FIELD;
    public static final Method PLUS_FN_METHOD;

    static {
        try {
            PLUS_HANDLE = MethodHandles.publicLookup().findStatic(VarUtil.class, "plusFn", MethodType.methodType(Long.TYPE, Long.TYPE, Long.TYPE));
            PLUS_VALUE_FIELD = VarUtil.class.getField("PLUS_HANDLE");
            PLUS_FN_METHOD = VarUtil.class.getMethod("plusFn", Long.TYPE, Long.TYPE);
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    public static final Type.FnType PLUS_TYPE = fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE);

    public static final Var PLUS_VAR = new Var(PLUS_TYPE, PLUS_TYPE, PLUS_VALUE_FIELD, PLUS_FN_METHOD);

    public static final NS FOO_NS = ns("foo");

    public static final Env PLUS_ENV = EMPTY.withVar(fqSym(USER, symbol("+")), PLUS_VAR).withNS(FOO_NS, HashTreePMap.singleton(symbol("u"), USER), Empty.map());
}