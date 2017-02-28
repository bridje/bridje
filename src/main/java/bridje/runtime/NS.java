package bridje.runtime;

import java.util.Objects;

public class NS {

    public static final NS CORE = ns("bridje.core");
    public static final NS USER = ns("user");

    public final String name;

    public static NS ns(String name) {
        return new NS(name);
    }

    private NS(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NS ns = (NS) o;
        return Objects.equals(name, ns.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return String.format("(NS %s)", name);
    }
}
