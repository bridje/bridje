package bridje.runtime;

import bridje.types.Type;
import org.pcollections.Empty;

import static bridje.Util.vectorOf;
import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.Symbol.symbol;

public interface IO<T> {
    FQSymbol IO_SYM = fqSym(NS.CORE, symbol("IO"));
    Type.DataTypeType IO_TYPE = new Type.DataTypeType(IO_SYM, IO.class);
    DataType<Type> DATA_TYPE = new DataType<>(IO_TYPE, IO_SYM, vectorOf(new Type.TypeVar()), Empty.vector());

    T runIO();
}
