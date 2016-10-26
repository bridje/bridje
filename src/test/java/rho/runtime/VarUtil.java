package rho.runtime;

import static rho.Util.vectorOf;
import static rho.runtime.Var.var;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SimpleType.INT_TYPE;

public class VarUtil {

    public static final Var PLUS_VAR = var(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE));

}