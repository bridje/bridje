package bridje.runtime;

import bridje.types.Type;
import org.pcollections.Empty;

import java.util.function.Supplier;

import static bridje.Util.vectorOf;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.Symbol.symbol;

public class IO<T> {
    public static final DataType<Type> DATA_TYPE;
    public static final FQSymbol IO_SYM = fqSym(NS.CORE, symbol("IO"));

    static {
        Type.TypeVar tv = new Type.TypeVar();
        DATA_TYPE = new DataType<>(new Type.DataTypeType(IO_SYM, IO.class),
            IO_SYM, vectorOf(tv), Empty.vector());
    }

    public final Supplier<T> supplier;

    public IO(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public T runIO() {
        return supplier.get();
    }
}
