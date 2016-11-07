package rho.types;

import rho.reader.Form;

import java.util.function.Function;

public class TypedExprData {

    public final Type type;
    public final Form form;

    public TypedExprData(Type type, Form form) {
        this.type = type;
        this.form = form;
    }

    public TypedExprData fmapType(Function<Type, Type> fn) {
        return new TypedExprData(fn.apply(type), form);
    }
}
