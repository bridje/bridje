package rho.analyser;

import org.junit.Test;
import rho.types.Type;

import static org.junit.Assert.assertEquals;
import static rho.reader.Form.SymbolForm.symbolForm;

public class TypeAnalyserTest {

    @Test
    public void builtInTypes() throws Exception {
        assertEquals(Type.SimpleType.STRING_TYPE, TypeAnalyser.analyzeType(symbolForm("Str")));
        assertEquals(Type.SimpleType.INT_TYPE, TypeAnalyser.analyzeType(symbolForm("Int")));
        assertEquals(Type.SimpleType.BOOL_TYPE, TypeAnalyser.analyzeType(symbolForm("Bool")));
    }
}