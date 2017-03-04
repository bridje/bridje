package bridje.analyser;

import bridje.reader.Form;
import bridje.runtime.NS;
import bridje.runtime.Symbol;
import org.pcollections.PVector;

public class ParseException extends RuntimeException {

    ParseException() {
    }

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ParseException(Throwable cause) {
        super(cause);
    }

    public static final class UnexpectedFormException extends ParseException {
        public final Object expected, actual;

        public UnexpectedFormException(Object expected, Object actual) {
            this.expected = expected;
            this.actual = actual;
        }
    }

    public static final class UnexpectedFormTypeException extends ParseException {

        final Form form;

        UnexpectedFormTypeException(Form form) {
            this.form = form;
        }
    }

    public static final class TypeParseException extends ParseException {

        public TypeParseException(Exception e) {
            super(e);
        }
    }

    public static final class ExtraFormsException extends ParseException {
        final PVector<Form> extraForms;

        ExtraFormsException(PVector<Form> extraForms) {
            this.extraForms = extraForms;
        }
    }

    public static final class MultipleAliasesInNS extends ParseException {
        final Form nsForm;

        MultipleAliasesInNS(Form nsForm) {
            this.nsForm = nsForm;
        }
    }

    public static final class DuplicateAliasException extends ParseException {
        final Form nsForm;
        final Symbol duplicateAlias;

        DuplicateAliasException(Form nsForm, Symbol duplicateAlias) {
            this.nsForm = nsForm;
            this.duplicateAlias = duplicateAlias;
        }
    }

    public static final class DuplicateReferException extends ParseException {
        final Form nsForm;
        final Symbol duplicateRefer;

        DuplicateReferException(Form nsForm, Symbol duplicateRefer) {
            this.nsForm = nsForm;
            this.duplicateRefer = duplicateRefer;
        }
    }

    public static final class MismatchingNSException extends ParseException {
        public final NS expectedNS;
        public final Form.SymbolForm symbolForm;

        public static ParseException mismatchedNS(NS expectedNS, Form.SymbolForm symbolForm) {
            return new MismatchingNSException(expectedNS, symbolForm);
        }

        MismatchingNSException(NS expectedNS, Form.SymbolForm symbolForm) {
            this.expectedNS = expectedNS;
            this.symbolForm = symbolForm;
        }
    }

    public static final class MultipleParseFailsException extends ParseException {
        final PVector<ListParser.ParseResult.Fail<?>> fails;

        MultipleParseFailsException(PVector<ListParser.ParseResult.Fail<?>> fails) {
            this.fails = fails;
        }
    }

    public static class MultipleRefersInNS extends ParseException {
        final Form nsForm;

        MultipleRefersInNS(Form nsForm) {
            this.nsForm = nsForm;
        }
    }

    public static class MultipleImportsInNS extends ParseException {
        final Form nsForm;

        MultipleImportsInNS(Form nsForm) {
            this.nsForm = nsForm;
        }
    }
}
