package bridje.types;

import bridje.Panic;
import bridje.types.Type.TypeVar;
import org.junit.Test;
import org.pcollections.HashTreePMap;

import static bridje.Util.vectorOf;
import static bridje.types.Type.SimpleType.INT_TYPE;
import static bridje.types.Type.SimpleType.STRING_TYPE;
import static bridje.types.Type.VectorType.vectorType;
import static org.junit.Assert.*;

public class TypeTest {

    @Test
    public void simpleTypeUnify() throws Exception {
        assertEquals(STRING_TYPE.unify(STRING_TYPE), TypeMapping.EMPTY);
        assertEquals(INT_TYPE.unify(INT_TYPE), TypeMapping.EMPTY);
    }

    @Test(expected = Panic.class)
    public void simpleThrow() throws Exception {
        STRING_TYPE.unify(INT_TYPE);
    }

    @Test
    public void vectorUnify() throws Exception {
        TypeVar var = new TypeVar();

        Type.VectorType vectorVarType = vectorType(var);
        Type.VectorType vectorConcreteType = vectorType(STRING_TYPE);

        TypeMapping mapping = vectorVarType.unify(vectorConcreteType);

        assertEquals(mapping, TypeMapping.from(HashTreePMap.singleton(var, STRING_TYPE)));
        assertEquals(vectorConcreteType, vectorVarType.apply(mapping));
    }

    @Test
    public void testAlphaEquivalence() throws Exception {
        assertTrue(new TypeVar().alphaEquivalentTo(new TypeVar()));

        Type t1 = new Type.FnType(vectorOf(vectorType(INT_TYPE)), new TypeVar());
        Type t2 = new Type.FnType(vectorOf(vectorType(INT_TYPE)), new TypeVar());
        assertTrue(t1.alphaEquivalentTo(t2));

        TypeVar a = new TypeVar();
        TypeVar b = new TypeVar();

        assertFalse(new Type.FnType(vectorOf(a), a).alphaEquivalentTo(new Type.FnType(vectorOf(a), b)));
    }
}