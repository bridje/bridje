package bridje.runtime;

import bridje.types.Type;
import org.pcollections.Empty;

import static bridje.Util.vectorOf;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.Symbol.symbol;

public class IO {
    public static final DataType<Type> DATA_TYPE;

    static {
        FQSymbol ioSym = fqSym(NS.CORE, symbol("IO"));
        Type.TypeVar tv = new Type.TypeVar();
        DATA_TYPE = new DataType<>(new Type.DataTypeType(ioSym, IO.class),
            ioSym, vectorOf(tv), Empty.vector());
    }

}
