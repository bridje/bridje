package bridje.runtime;

import org.pcollections.PVector;

public class DataType {
    public final FQSymbol sym;
    public final PVector<DataTypeConstructor> constructors;

    public DataType(FQSymbol sym, PVector<DataTypeConstructor> constructors) {
        this.sym = sym;
        this.constructors = constructors;
    }
}
