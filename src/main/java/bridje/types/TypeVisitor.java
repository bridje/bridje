package bridje.types;

public interface TypeVisitor<T> {
    T visitBool();

    T visitString();

    T visitLong();

    T visitEnvIO();

    T visit(Type.VectorType type);

    T visit(Type.SetType type);

    T visit(Type.MapType type);

    T visit(Type.FnType type);

    T visit(Type.TypeVar type);

    T visit(Type.DataTypeType type);

    T visit(Type.AppliedType type);
}
