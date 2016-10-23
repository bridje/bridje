package rho.reader;

import java.util.Objects;

public abstract class Form {

    public final Range range;

    private Form(Range range) {
        this.range = range;
    }

    public static final class StringForm extends Form {
        public final String string;

        public StringForm(Range range, String string) {
            super(range);
            this.string = string;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringForm that = (StringForm) o;
            return Objects.equals(string, that.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(string);
        }

        @Override
        public String toString() {
            return String.format("(StringForm \"%s\")", string);
        }
    }

    public static class IntForm extends Form {
        public final long num;

        public IntForm(Range range, long num) {
            super(range);
            this.num = num;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntForm intForm = (IntForm) o;
            return num == intForm.num;
        }

        @Override
        public int hashCode() {
            return Objects.hash(num);
        }

        @Override
        public String toString() {
            return String.format("(IntForm \"%s\")", num);
        }
    }
}
