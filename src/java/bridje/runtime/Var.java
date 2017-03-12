package bridje.runtime;

import bridje.runtime.java.JavaField;

public class Var {
    public final JavaField valueField;

    public Var(JavaField valueField) {
        this.valueField = valueField;
    }
}
