package bridje.runtime;

public interface ConstructorVisitor<U> {

    U visit(DataTypeConstructor.ValueConstructor constructor);

    U visit(DataTypeConstructor.VectorConstructor constructor);
}
