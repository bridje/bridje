package rho.runtime;

import rho.types.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Var {

    public static final String VALUE_FIELD_NAME = "$$value";
    public static final String FN_METHOD_NAME = "$$invoke";

    public final Type declaredType;
    public final Type inferredType;

    public final Field valueField;
    public final Method fnMethod;

    public Var(Type declaredType, Type inferredType, Field valueField, Method fnMethod) {
        this.declaredType = declaredType;
        this.inferredType = inferredType;
        this.valueField = valueField;
        this.fnMethod = fnMethod;
    }
}
