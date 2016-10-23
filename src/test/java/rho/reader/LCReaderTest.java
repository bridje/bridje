package rho.reader;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static rho.reader.Location.loc;

public class LCReaderTest {

    @Test
    public void testLCReader() throws Exception {
        String testString = "Hello\nworld";
        LCReader reader = LCReader.fromString(testString);

        assertEquals(loc(1, 1), reader.location());

        reader.read(); // H
        assertEquals('e', reader.read());
        assertEquals(loc(1, 3), reader.location());

        reader.read(); // l
        reader.read(); // l
        reader.read(); // o
        reader.read(); // \n
        reader.read(); // W

        assertEquals('o', reader.read());
        assertEquals(loc(2, 3), reader.location());

        reader.unread('x');
        assertEquals(loc(2, 2), reader.location());
        assertEquals('x', reader.read());

        reader.read(); // r
        reader.read(); // l
        reader.read(); // d

        assertEquals(-1, reader.read());
    }
}