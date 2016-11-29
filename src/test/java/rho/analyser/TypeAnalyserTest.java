package rho.analyser;

import org.junit.Test;
import rho.types.Type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.SymbolForm.symbolForm;

public class TypeAnalyserTest {

    @Test
    public void builtInTypes() throws Exception {
        assertEquals(Type.SimpleType.STRING_TYPE, TypeAnalyser.analyzeType(symbolForm("Str"), LocalTypeEnv.EMPTY));
        assertEquals(Type.SimpleType.INT_TYPE, TypeAnalyser.analyzeType(symbolForm("Int"), LocalTypeEnv.EMPTY));
        assertEquals(Type.SimpleType.BOOL_TYPE, TypeAnalyser.analyzeType(symbolForm("Bool"), LocalTypeEnv.EMPTY));
    }

    @Test
    public void identityFn() throws Exception {
        Type.FnType type = (Type.FnType) TypeAnalyser.analyzeType(listForm(symbolForm("Fn"), symbolForm("a"), symbolForm("a")), LocalTypeEnv.EMPTY);
        assertEquals(1, type.paramTypes.size());
        assertTrue(type.returnType instanceof Type.TypeVar);
        assertEquals(type.returnType, type.paramTypes.get(0));
    }
}