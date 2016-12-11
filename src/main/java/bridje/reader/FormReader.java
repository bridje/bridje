package bridje.reader;

import bridje.Panic;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;

import static bridje.Panic.panic;
import static bridje.reader.Range.range;
import static bridje.runtime.Symbol.symbol;

public class FormReader {

    private static boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch) || ',' == ch;
    }

    private static Panic eof(LCReader reader) {
        return panic("EOF while reading (%s)", reader.location());
    }

    private static Panic eof(LCReader reader, char endChar) {
        return panic("EOF while reading, expected '%s' (%s)", endChar, reader.location());
    }

    private static final Dispatcher[] DISPATCH_CHARS = new Dispatcher[256];
    private static final Dispatcher[] SECONDARY_DISPATCH_CHARS = new Dispatcher[256];

    private interface Dispatcher {
        Form read(LCReader reader, Character endChar);
    }

    private static class StringDispatcher implements Dispatcher {
        @Override
        public Form read(LCReader reader, Character endChar) {
            Location start = reader.location();

            reader.read(); // \"

            StringBuilder sb = new StringBuilder();

            while (true) {
                int ch = reader.read();

                switch (ch) {
                    case -1:
                        throw eof(reader, '"');
                    case '"':
                        return new Form.StringForm(range(start, reader.location()), sb.toString());
                    case '\\':
                        int nextCh = reader.read();
                        switch (nextCh) {
                            case -1:
                                throw eof(reader);
                            case 'n':
                                sb.append('\n');
                                break;
                            case 'r':
                                sb.append('\r');
                                break;
                            case 't':
                                sb.append('\t');
                                break;
                            case '"':
                                sb.append('"');
                                break;
                            case '\\':
                                sb.append('\\');
                                break;
                            default:
                                throw panic("Unexpected escape char: '\\%s'", (char) nextCh);
                        }

                        break;
                    default:
                        sb.append((char) ch);
                }
            }
        }
    }

    private static class UnmatchedDelimiterDispatcher implements Dispatcher {

        private final char delimiter;

        UnmatchedDelimiterDispatcher(char delimiter) {
            this.delimiter = delimiter;
        }

        @Override
        public Form read(LCReader reader, Character endChar) {
            throw panic("Unexpected delimiter '%s' at %s%s", delimiter, reader.location(),
                endChar != null ? String.format(", expecting '%s'", endChar) : "");
        }
    }

    private static class CollectionDispatcher implements Dispatcher {
        private final String startChar;
        private final char endChar;
        private final BiFunction<Range, PVector<Form>, Form> formCreator;

        public CollectionDispatcher(String startChar, char endChar, BiFunction<Range, PVector<Form>, Form> formCreator) {

            this.startChar = startChar;
            this.endChar = endChar;
            this.formCreator = formCreator;
        }

        @Override
        public Form read(LCReader reader, Character endChar) {
            Location start = reader.location();

            for (int i = 0; i < startChar.length(); i++) {
                reader.read();
            }

            List<Form> forms = new LinkedList<>();

            Form form;
            do {
                form = read0(reader, this.endChar, EOFBehaviour.THROW);

                if (form != null) {
                    forms.add(form);
                }
            } while (form != null);

            return formCreator.apply(range(start, reader.location()), TreePVector.from(forms));
        }
    }

    private static class SecondaryDispatcher implements Dispatcher {

        @Override
        public Form read(LCReader reader, Character endChar) {
            reader.read();
            int secondaryCh = reader.read();

            if (-1 == secondaryCh) {
                throw eof(reader);
            }

            reader.unread(secondaryCh);
            reader.unread('^');

            Dispatcher dispatcher = SECONDARY_DISPATCH_CHARS[secondaryCh];

            if (dispatcher == null) {
                throw panic("Unexpected token: '^%s' (%s)", (char) secondaryCh, reader.location());
            } else {
                return dispatcher.read(reader, endChar);
            }
        }
    }

    static {
        DISPATCH_CHARS['"'] = new StringDispatcher();
        DISPATCH_CHARS[')'] = new UnmatchedDelimiterDispatcher(')');
        DISPATCH_CHARS[']'] = new UnmatchedDelimiterDispatcher(']');
        DISPATCH_CHARS['}'] = new UnmatchedDelimiterDispatcher('}');

        DISPATCH_CHARS['['] = new CollectionDispatcher("[", ']', Form.VectorForm::new);
        DISPATCH_CHARS['('] = new CollectionDispatcher("(", ')', Form.ListForm::new);
        DISPATCH_CHARS['{'] = new CollectionDispatcher("{", '}', (range, forms) -> new Form.RecordForm(range, readRecordEntries(forms)));

        DISPATCH_CHARS['^'] = new SecondaryDispatcher();
        SECONDARY_DISPATCH_CHARS['['] = new CollectionDispatcher("^[", ']', Form.SetForm::new);
        SECONDARY_DISPATCH_CHARS['{'] = new CollectionDispatcher("^{", '}', (range, forms) -> new Form.MapForm(range, readMapEntries(forms)));
    }

    private static PVector<Form.MapForm.MapEntryForm> readMapEntries(PVector<Form> forms) {
        if (forms.size() % 2 != 0) {
            throw new UnsupportedOperationException();
        }

        Collection<Form.MapForm.MapEntryForm> entryForms = new LinkedList<>();

        for (int i = 0; i < forms.size(); i += 2) {
            Form key = forms.get(i);
            Form value = forms.get(i + 1);
            entryForms.add(new Form.MapForm.MapEntryForm(range(key.range.start, value.range.end), key, value));
        }

        return TreePVector.from(entryForms);
    }

    private static PVector<Form.RecordForm.RecordEntryForm> readRecordEntries(PVector<Form> forms) {
        if (forms.size() % 2 != 0) {
            throw new UnsupportedOperationException();
        }

        Collection<Form.RecordForm.RecordEntryForm> entryForms = new LinkedList<>();

        for (int i = 0; i < forms.size(); i += 2) {
            Form keyForm = forms.get(i);

            if (!(keyForm instanceof Form.SymbolForm)) {
                throw new UnsupportedOperationException();
            }

            Form.SymbolForm key = ((Form.SymbolForm) keyForm);
            Form value = forms.get(i + 1);
            entryForms.add(new Form.RecordForm.RecordEntryForm(range(key.range.start, value.range.end), key.sym, value));
        }

        return TreePVector.from(entryForms);
    }

    private static String readToken(LCReader rdr, Character endChar, EOFBehaviour eofBehaviour) {
        StringBuilder sb = new StringBuilder();

        while (true) {
            int ch = rdr.read();

            if (-1 == ch && eofBehaviour.reactToEOF(rdr.location(), endChar)) {
                rdr.unread(ch);
                return sb.toString();
            } else if (DISPATCH_CHARS[ch] != null || isWhitespace((char) ch) || (endChar != null && ch == endChar)) {
                rdr.unread(ch);
                return sb.toString();
            } else {
                sb.append((char) ch);
            }
        }
    }

    private static Form readNumber(LCReader reader, Character endChar, EOFBehaviour eofBehaviour) {
        Location start = reader.location();
        String token = readToken(reader, endChar, eofBehaviour);
        Range range = range(start, reader.location());

        try {
            return new Form.IntForm(range, Long.parseLong(token));
        } catch (NumberFormatException e) {
            throw panic("Invalid number '%s' (%s)", token, range);
        }
    }

    private static Form readSymbol(LCReader reader, Character endChar, EOFBehaviour eofBehaviour) {
        Location start = reader.location();
        String token = readToken(reader, endChar, eofBehaviour);
        Range range = range(start, reader.location());

        switch (token) {
            case "true":
                return new Form.BoolForm(range, true);
            case "false":
                return new Form.BoolForm(range, false);
            default:
                int slashIndex = token.indexOf('/');
                if (-1 != slashIndex) {
                    return new Form.QSymbolForm(range, token.substring(0, slashIndex), token.substring(slashIndex + 1));
                } else {
                    return new Form.SymbolForm(range, symbol(token));
                }
        }
    }

    private static int readNextChar(LCReader reader) {
        while (true) {
            int ch = reader.read();
            if (-1 == ch) {
                return -1;
            }

            if (';' == ch) {
                while ('\n' != ch) {
                    ch = reader.read();

                    if (-1 == ch) {
                        return -1;
                    }
                }

                continue;
            }

            if (!isWhitespace((char) ch)) {
                return ch;
            }
        }
    }

    private static Form read0(LCReader reader, Character endChar, EOFBehaviour eofBehaviour) {
        int ch = readNextChar(reader);

        if (-1 == ch) {
            reader.unread(ch);
            eofBehaviour.reactToEOF(reader.location(), endChar);
            return null;
        }

        if (endChar != null && ch == endChar) {
            return null;
        }

        reader.unread(ch);
        Dispatcher dispatcher = DISPATCH_CHARS[ch];

        if (dispatcher != null) {
            return dispatcher.read(reader, endChar);
        }

        if (Character.isDigit((char) ch) || '-' == ch) {
            return readNumber(reader, endChar, eofBehaviour);
        }

        return readSymbol(reader, endChar, eofBehaviour);
    }

    public static Form read(LCReader reader) {
        return read0(reader, null, EOFBehaviour.RETURN);
    }
}
