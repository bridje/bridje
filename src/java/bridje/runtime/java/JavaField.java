package bridje.runtime.java;

import bridje.util.ClassLike;

public class JavaField {
    public final ClassLike owner;
    public final String name;
    public final ClassLike clazz;

    public JavaField(ClassLike owner, String name, ClassLike clazz) {
        this.owner = owner;
        this.name = name;
        this.clazz = clazz;
    }
}
