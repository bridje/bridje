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

    public abstract <T> T accept(FormVisitor<T> visitor);

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

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.accept(this);
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
            return String.format("(IntForm %s)", num);
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.accept(this);
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

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.accept(this);
        }
    }

    public static class SetForm extends Form {

        public final PVector<Form> forms;

        static SetForm setForm(Form... forms) {
            return new SetForm(null, TreePVector.from(Arrays.asList(forms)));
        }

        public SetForm(Range range, PVector<Form> forms) {
            super(range);
            this.forms = forms;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SetForm that = (SetForm) o;
            return Objects.equals(forms, that.forms);
        }

        @Override
        public int hashCode() {
            return Objects.hash(forms);
        }

        @Override
        public String toString() {
            return String.format("(SetForm %s)", forms.stream().map(Object::toString).collect(Collectors.joining(" ")));
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.accept(this);
        }
    }

    public static class ListForm extends Form {

        public final PVector<Form> forms;

        static ListForm listForm(Form... forms) {
            return new ListForm(null, TreePVector.from(Arrays.asList(forms)));
        }

        public ListForm(Range range, PVector<Form> forms) {
            super(range);
            this.forms = forms;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListForm that = (ListForm) o;
            return Objects.equals(forms, that.forms);
        }

        @Override
        public int hashCode() {
            return Objects.hash(forms);
        }

        @Override
        public String toString() {
            return String.format("(ListForm %s)", forms.stream().map(Object::toString).collect(Collectors.joining(" ")));
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.accept(this);
        }
    }

    public static final class SymbolForm extends Form {
        public final String sym;

        static SymbolForm symbolForm(String sym) {
            return new SymbolForm(null, sym);
        }

        public SymbolForm(Range range, String sym) {
            super(range);
            this.sym = sym;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SymbolForm that = (SymbolForm) o;
            return Objects.equals(sym, that.sym);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sym);
        }

        @Override
        public String toString() {
            return String.format("(SymbolForm \"%s\")", sym);
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.accept(this);
        }
    }

    public static final class QSymbolForm extends Form {
        public final String ns;
        public final String sym;

        static QSymbolForm qSymbolForm(String ns, String sym) {
            return new QSymbolForm(null, ns, sym);
        }

        public QSymbolForm(Range range, String ns, String sym) {
            super(range);
            this.ns = ns;
            this.sym = sym;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QSymbolForm that = (QSymbolForm) o;
            return Objects.equals(ns, that.ns) &&
                Objects.equals(sym, that.sym);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ns, sym);
        }

        @Override
        public String toString() {
            return String.format("(QSymbolForm \"%s/%s\")", ns, sym);
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.accept(this);
        }
    }
}
