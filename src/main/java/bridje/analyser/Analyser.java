package bridje.analyser;

import bridje.reader.Form;
import bridje.runtime.NS;

import static bridje.analyser.ListParser.*;
import static bridje.analyser.Language.CORE_LANGUAGE;
import static bridje.runtime.Symbol.symbol;

public class Analyser {


    public static NSDeclaration parseNSDeclaration(Form form) {
        return parseForm(form, LIST_PARSER.bind(listForm ->
            nestedListParser(listForm.forms,
                SYMBOL_PARSER.bind(nsSymForm -> {
                    if (!nsSymForm.sym.equals(symbol("ns"))) {
                        return fail(new ParseException.UnexpectedFormException("ns", nsSymForm.sym));
                    } else {
                        return SYMBOL_PARSER.bind(nsNameForm -> {
                            NS ns = NS.ns(nsNameForm.sym.sym);

                            return anyOf(
                                parseEnd(new NSDeclaration(ns, CORE_LANGUAGE)),
                                RECORD_PARSER.bind(nsOptsRecordForm ->
                                    // TODO parse this
                                    parseEnd(new NSDeclaration(ns, CORE_LANGUAGE))));
                        });
                    }
                }),
                ListParser::pure)))
            .orThrow().left;
    }
}
