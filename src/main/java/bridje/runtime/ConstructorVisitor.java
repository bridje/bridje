package bridje.runtime;

public interface ConstructorVisitor<T, U> {

    U visit(DataTypeConstructor.ValueConstructor<? extends T> constructor);
    U visit(DataTypeConstructor.VectorConstructor<? extends T> constructor);
}
