package bridje.types;

import bridje.types.Type.TypeVar;
import org.junit.Test;

import static bridje.Util.vectorOf;
import static bridje.types.Type.SimpleType.INT_TYPE;
import static bridje.types.Type.VectorType.vectorType;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TypeTest {

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