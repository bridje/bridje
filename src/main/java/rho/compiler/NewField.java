package rho.compiler;

import org.pcollections.PSet;

public class NewField {
    public final String name;
    public final Class<?> clazz;
    public final PSet<AccessFlag> flags;

    public static NewField newField(String name, Class<?> clazz, PSet<AccessFlag> flags) {
        return new NewField(name, clazz, flags);
    }

    public NewField(String name, Class<?> clazz, PSet<AccessFlag> flags) {
        this.name = name;
        this.clazz = clazz;
        this.flags = flags;
    }
}
