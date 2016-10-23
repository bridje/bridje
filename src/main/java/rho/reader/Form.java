package rho.reader;

import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Form {

    public final Range range;

    private Form(Range range) {
        this.range = range;
    }

    public static final class StringForm extends Form {
        public final String string;

        static StringForm stringForm(String string) {
            return new StringForm(null, string);
        }

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

        static IntForm intForm(long num) {
            return new IntForm(null, num);
        }

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

    public static class VectorForm extends Form {

        public final PVector<Form> forms;

        static VectorForm vectorForm(Form... forms) {
            return new VectorForm(null, TreePVector.from(Arrays.asList(forms)));
        }

        public VectorForm(Range range, PVector<Form> forms) {
            super(range);
            this.forms = forms;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VectorForm that = (VectorForm) o;
            return Objects.equals(forms, that.forms);
        }

        @Override
        public int hashCode() {
            return Objects.hash(forms);
        }

        @Override
        public String toString() {
            return String.format("(VectorForm %s)", forms.stream().map(Object::toString).collect(Collectors.joining(" ")));
        }
    }
}
