package rho.reader;

import java.util.Objects;

import static rho.reader.Location.loc;

public class Range {

    public final Location start;
    public final Location end;

    public static Range range(Location start, Location end) {
        return new Range(start, loc(end.line, end.col - 1));
    }

    private Range(Location start, Location end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return Objects.equals(start, range.start) &&
            Objects.equals(end, range.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return String.format("%s-%s", start, end);
    }
}
