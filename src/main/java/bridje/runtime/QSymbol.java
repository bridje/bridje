package bridje.runtime;

import java.util.Objects;

public class QSymbol {
    public final String ns;
    public final String symbol;

    public QSymbol(String ns, String symbol) {
        this.ns = ns;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QSymbol qSymbol = (QSymbol) o;
        return Objects.equals(ns, qSymbol.ns) &&
            Objects.equals(symbol, qSymbol.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ns, symbol);
    }

    @Override
    public String toString() {
        return String.format("%s/%s", ns, symbol);
    }
}
