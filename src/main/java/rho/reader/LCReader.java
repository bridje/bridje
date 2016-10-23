package rho.reader;

import java.io.*;
import java.util.Stack;

import static rho.Panic.panic;
import static rho.reader.Location.loc;

public class LCReader implements Closeable {

    public static LCReader fromFile(File file) throws FileNotFoundException {
        return fromReader(new FileReader(file));
    }

    public static LCReader fromResource(String path) {
        return fromReader(new InputStreamReader(LCReader.class.getClassLoader().getResourceAsStream(path)));
    }

    public static LCReader fromString(String form) {
        return fromReader(new StringReader(form));
    }

    public static LCReader fromReader(Reader reader) {
        return new LCReader(reader);
    }

    private final LineNumberReader lineNumberReader;
    private final Stack<Integer> stack = new Stack<>();
    private int readerColNumber = 1;

    private LCReader(Reader reader) {
        lineNumberReader = new LineNumberReader(reader);
    }

    int read() {
        if (stack.isEmpty()) {
            int ch;

            try {
                ch = lineNumberReader.read();
            } catch (IOException e) {
                throw panic("IOException while reading", e);
            }

            if (ch == '\n') {
                readerColNumber = 0;
            }

            readerColNumber++;

            return ch;

        } else {
            return stack.pop();
        }
    }

    void unread(int ch) {
        if (!Character.isWhitespace(ch)) {
            stack.push(ch);
        }
    }

    Location location() {
        return loc(lineNumberReader.getLineNumber() + 1, readerColNumber - stack.size());
    }

    @Override
    public void close() throws IOException {
        lineNumberReader.close();
    }
}
