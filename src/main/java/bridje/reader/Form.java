package bridje.reader;

import bridje.runtime.QSymbol;
import bridje.runtime.Symbol;
import org.pcollections.PCollection;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.util.Arrays;
import java.util.Objects;

import static bridje.runtime.Symbol.symbol;
import static java.util.stream.Collectors.joining;

public abstract class Form {

    public final Range range;

    private Form(Range range) {
        this.range = range;
    }

    public abstract <T> T accept(FormVisitor<T> visitor);

    public static final class BoolForm extends Form {

        public final boolean value;

        public static BoolForm boolForm(boolean value) {
            return new BoolForm(null, value);
        }

        public BoolForm(Range range, boolean value) {
            super(range);
            this.value = value;
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoolForm boolForm = (BoolForm) o;
            return value == boolForm.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return String.format("(BoolForm %s)", value);
        }
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

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static class IntForm extends Form {
        public final long num;

        public static IntForm intForm(long num) {
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
            return visitor.visit(this);
        }
    }

    public static class VectorForm extends Form {

        public final PVector<Form> forms;

        public static VectorForm vectorForm(Form... forms) {
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
            return String.format("(VectorForm %s)", forms.stream().map(Object::toString).collect(joining(" ")));
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.visit(this);
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
            return String.format("(SetForm %s)", forms.stream().map(Object::toString).collect(joining(" ")));
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static class RecordForm extends Form {

        public static class RecordEntryForm {
            public final Range range;
            public final Symbol key;
            public final Form value;

            public RecordEntryForm(Range range, Symbol key, Form value) {
                this.range = range;
                this.key = key;
                this.value = value;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                RecordEntryForm that = (RecordEntryForm) o;
                return Objects.equals(key, that.key) &&
                    Objects.equals(value, that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(key, value);
            }

            @Override
            public String toString() {
                return String.format("(RecordEntryForm %s=%s)", key, value);
            }
        }

        public final PCollection<RecordEntryForm> entries;

        public RecordForm(Range range, PCollection<RecordEntryForm> entries) {
            super(range);
            this.entries = entries;
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecordForm recordForm = (RecordForm) o;
            return Objects.equals(entries, recordForm.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries);
        }

        @Override
        public String toString() {
            return String.format("(RecordForm {%s})", entries.stream().map(ref -> String.format("%s %s", ref.key, ref.value)).collect(joining(", ")));
        }
    }

    public static class ListForm extends Form {

        public final PVector<Form> forms;

        public static ListForm listForm(Form... forms) {
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
            return String.format("(ListForm %s)", forms.stream().map(Object::toString).collect(joining(" ")));
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class SymbolForm extends Form {
        public final Symbol sym;

        public static SymbolForm symbolForm(String sym) {
            return new SymbolForm(null, symbol(sym));
        }

        public SymbolForm(Range range, Symbol sym) {
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
            return visitor.visit(this);
        }
    }

    public static final class QSymbolForm extends Form {
        public final QSymbol qsym;

        public static QSymbolForm qSymbolForm(String ns, String sym) {
            return new QSymbolForm(null, new QSymbol(ns, sym));
        }

        public QSymbolForm(Range range, QSymbol qsym) {
            super(range);
            this.qsym = qsym;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QSymbolForm that = (QSymbolForm) o;
            return Objects.equals(qsym, that.qsym);
        }

        @Override
        public int hashCode() {
            return Objects.hash(qsym);
        }

        @Override
        public String toString() {
            return String.format("(QSymbolForm %s)", qsym);
        }

        @Override
        public <T> T accept(FormVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }
}
