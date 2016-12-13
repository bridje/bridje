package bridje.analyser;

import bridje.runtime.NS;
import bridje.types.Type;
import org.junit.Test;

import static bridje.reader.Form.ListForm.listForm;
import static bridje.reader.Form.SymbolForm.symbolForm;
import static bridje.runtime.Env.CORE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeAnalyserTest {

    @Test
    public void builtInTypes() throws Exception {
        assertEquals(Type.SimpleType.STRING_TYPE, TypeAnalyser.analyzeType(CORE, LocalTypeEnv.EMPTY, NS.CORE, symbolForm("Str")));
        assertEquals(Type.SimpleType.INT_TYPE, TypeAnalyser.analyzeType(CORE, LocalTypeEnv.EMPTY, NS.CORE, symbolForm("Int")));
        assertEquals(Type.SimpleType.BOOL_TYPE, TypeAnalyser.analyzeType(CORE, LocalTypeEnv.EMPTY, NS.CORE, symbolForm("Bool")));
    }

    @Test
    public void identityFn() throws Exception {
        Type.FnType type = (Type.FnType) TypeAnalyser.analyzeType(CORE, LocalTypeEnv.EMPTY, NS.CORE, listForm(symbolForm("Fn"), symbolForm("a"), symbolForm("a")));
        assertEquals(1, type.paramTypes.size());
        assertTrue(type.returnType instanceof Type.TypeVar);
        assertEquals(type.returnType, type.paramTypes.get(0));
    }
}