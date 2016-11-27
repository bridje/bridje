package rho.runtime;

import org.pcollections.PVector;

public class DataType {
    public final Symbol sym;
    public final PVector<DataTypeConstructor> constructors;

    public DataType(Symbol sym, PVector<DataTypeConstructor> constructors) {
        this.sym = sym;
        this.constructors = constructors;
    }
}
