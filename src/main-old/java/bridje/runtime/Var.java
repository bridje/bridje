package bridje.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Var {

    public static final String VALUE_FIELD_NAME = "$$value";
    public static final String FN_METHOD_NAME = "$$invoke";

    public final Field valueField;
    public final Method fnMethod;

    public Var(Field valueField, Method fnMethod) {
        this.valueField = valueField;
        this.fnMethod = fnMethod;
    }
}
