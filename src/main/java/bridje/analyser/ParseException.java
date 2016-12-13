package bridje.analyser;

import bridje.reader.Form;
import bridje.runtime.Symbol;
import org.pcollections.PVector;

class ParseException extends RuntimeException {

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

    static final class UnexpectedFormTypeException extends ParseException {

        final Form form;

        UnexpectedFormTypeException(Form form) {
            this.form = form;
        }
    }

    static final class TypeParseException extends ParseException {

        public TypeParseException(Exception e) {
            super(e);
        }
    }

    static final class ExtraFormsException extends ParseException {
        final PVector<Form> extraForms;

        ExtraFormsException(PVector<Form> extraForms) {
            this.extraForms = extraForms;
        }
    }

    static final class MultipleAliasesInNS extends ParseException {
        final Form nsForm;

        MultipleAliasesInNS(Form nsForm) {
            this.nsForm = nsForm;
        }
    }

    static final class DuplicateAliasException extends ParseException {
        final Form nsForm;
        final Symbol duplicateAlias;

        DuplicateAliasException(Form nsForm, Symbol duplicateAlias) {
            this.nsForm = nsForm;
            this.duplicateAlias = duplicateAlias;
        }
    }

    static final class DuplicateReferException extends ParseException {
        final Form nsForm;
        final Symbol duplicateRefer;

        DuplicateReferException(Form nsForm, Symbol duplicateRefer) {
            this.nsForm = nsForm;
            this.duplicateRefer = duplicateRefer;
        }
    }


    static final class MultipleParseFailsException extends ParseException {
        final PVector<ListParser.ParseResult.Fail<?>> fails;

        MultipleParseFailsException(PVector<ListParser.ParseResult.Fail<?>> fails) {
            this.fails = fails;
        }
    }

    static class MultipleRefersInNS extends ParseException {
        final Form nsForm;

        MultipleRefersInNS(Form nsForm) {
            this.nsForm = nsForm;
        }
    }

    static class MultipleImportsInNS extends ParseException {
        final Form nsForm;

        MultipleImportsInNS(Form nsForm) {
            this.nsForm = nsForm;
        }
    }
}
