package rho.analyser;

import org.pcollections.PVector;
import rho.reader.Form;

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
}
