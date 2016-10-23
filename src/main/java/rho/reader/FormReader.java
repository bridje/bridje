package rho.reader;

import rho.Panic;

import static rho.Panic.panic;
import static rho.reader.Range.range;

public class FormReader {

    private static boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch) || ',' == ch;
    }

    private static Panic eof(LCReader reader) {
        return panic(String.format("EOF while reading (%s)", reader.location()));
    }

    private static Panic eof(LCReader reader, char endChar) {
        return panic(String.format("EOF while reading, expected '%s' (%s)", endChar, reader.location()));
    }

    private static Dispatcher[] DISPATCH_CHARS = new Dispatcher[256];
    private static Dispatcher[] SECONDARY_DISPATCH_CHARS = new Dispatcher[256];

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
                                throw panic(String.format("Unexpected escape char: '\\%s'", (char) nextCh));
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
            throw panic(String.format("Unexpected delimiter '%s' at %s%s", delimiter, reader.location(),
                endChar != null ? String.format(", expecting '%s'", endChar) : ""));
        }
    }

    static {
        DISPATCH_CHARS['"'] = new StringDispatcher();
        DISPATCH_CHARS[')'] = new UnmatchedDelimiterDispatcher(')');
        DISPATCH_CHARS[']'] = new UnmatchedDelimiterDispatcher(']');
        DISPATCH_CHARS['}'] = new UnmatchedDelimiterDispatcher('}');
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
            throw panic(String.format("Invalid number '%s' (%s)", token, range));
        }
    }

    private static Form read0(LCReader reader, Character endChar, EOFBehaviour eofBehaviour) {
        int ch;

        while (true) {
            ch = reader.read();
            if (-1 == ch) {
                return null;
            }

            if (!isWhitespace((char) ch)) {
                break;
            }
        }

        Dispatcher dispatcher = DISPATCH_CHARS[ch];

        if (dispatcher != null) {
            reader.unread(ch);
            return dispatcher.read(reader, endChar);
        }

        if (Character.isDigit((char) ch) || '-' == ch) {
            reader.unread(ch);
            return readNumber(reader, endChar, eofBehaviour);
        }

        return null;
    }

    public static Form read(LCReader reader) {
        return read0(reader, null, EOFBehaviour.RETURN);
    }
}
