package bridje.compiler;

import bridje.util.ClassLike;
import org.pcollections.PSet;

public class NewField {
    public final String name;
    public final ClassLike clazz;
    public final PSet<AccessFlag> flags;

    public static NewField newField(PSet<AccessFlag> flags, String name, ClassLike clazz) {
        return new NewField(name, clazz, flags);
    }

    public NewField(String name, ClassLike clazz, PSet<AccessFlag> flags) {
        this.name = name;
        this.clazz = clazz;
        this.flags = flags;
    }
}
