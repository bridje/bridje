package rho.reader;

import org.junit.Test;
import rho.Panic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.StringForm.stringForm;
import static rho.reader.Form.VectorForm.vectorForm;
import static rho.reader.Location.loc;
import static rho.reader.Range.range;

public class FormReaderTest {

    @Test
    public void readsString() throws Exception {
        Form form = FormReader.read(LCReader.fromString("\"Hello world!\""));

        assertNotNull(form);
        assertEquals(new Form.StringForm(null, "Hello world!"), form);
        assertEquals(range(loc(1, 1), loc(1, 15)), form.range);
    }

    @Test
    public void readsStringWithEscapes() throws Exception {
        Form form = FormReader.read(LCReader.fromString("\"He\\tllo\\n\n\\\"world\\\"!\""));

        assertNotNull(form);
        assertEquals(new Form.StringForm(null, "He\tllo\n\n\"world\"!"), form);
    }

    @Test
    public void slurpsWhitespace() throws Exception {
        Form form = FormReader.read(LCReader.fromString("  \n   \"Hello world!\""));

        assertNotNull(form);
        assertEquals(stringForm("Hello world!"), form);
        assertEquals(range(loc(2, 4), loc(2, 18)), form.range);
    }

    @Test
    public void readsPositiveInt() throws Exception {
        Form form = FormReader.read(LCReader.fromString("1532"));
        assertNotNull(form);
        assertEquals(intForm(1532), form);
        assertEquals(range(loc(1, 1), loc(1, 5)), form.range);
    }

    @Test
    public void readsNegativeInt() throws Exception {
        Form form = FormReader.read(LCReader.fromString("-1532"));
        assertNotNull(form);
        assertEquals(intForm(-1532), form);
        assertEquals(range(loc(1, 1), loc(1, 6)), form.range);
    }

    @Test(expected = Panic.class)
    public void barfsOnInvalidNumber() throws Exception {
        FormReader.read(LCReader.fromString("-15f32"));
    }

    @Test
    public void readsVector() throws Exception {
        Form form = FormReader.read(LCReader.fromString("[\"Hello\", \"world!\"]"));

        assertNotNull(form);
        assertEquals(vectorForm(stringForm("Hello"), stringForm("world!")), form);
        assertEquals(range(loc(1, 1), loc(1, 20)), form.range);
    }
}