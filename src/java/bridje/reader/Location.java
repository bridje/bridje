package bridje.reader;

import java.util.Objects;

public class Location {
    public final int line;
    public final int col;

    static Location loc(int line, int col) {
        return new Location(line, col);
    }

    private Location(int line, int col) {
        this.line = line;
        this.col = col;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return line == location.line &&
            col == location.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, col);
    }

    @Override
    public String toString() {
        return String.format("L%dC%d", line, col);
    }
}
