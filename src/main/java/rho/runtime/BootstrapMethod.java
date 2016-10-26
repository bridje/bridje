package rho.runtime;

import java.lang.invoke.MethodType;

public class BootstrapMethod {
    public final Class<?> bootstrapClass;
    public final String bootstrapMethodName;
    public final MethodType methodType;

    public BootstrapMethod(Class<?> bootstrapClass, String bootstrapMethodName, MethodType methodType) {
        this.bootstrapClass = bootstrapClass;
        this.bootstrapMethodName = bootstrapMethodName;
        this.methodType = methodType;
    }
}
