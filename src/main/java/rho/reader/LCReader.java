package rho.reader;

import java.io.*;
import java.util.Stack;

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

    int read() throws IOException {
        if (stack.isEmpty()) {
            int ch = lineNumberReader.read();

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
        return new Location(lineNumberReader.getLineNumber() + 1, readerColNumber - stack.size());
    }

    @Override
    public void close() throws IOException {
        lineNumberReader.close();
    }
}
