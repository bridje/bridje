package rho.types;

import org.junit.Test;
import org.pcollections.Empty;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import rho.Panic;
import rho.types.Type.TypeVar;

import static org.junit.Assert.assertEquals;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.types.Type.SimpleType.STRING_TYPE;
import static rho.types.Type.VectorType.vectorType;

public class TypeTest {

    @Test
    public void simpleTypeUnify() throws Exception {
        assertEquals(STRING_TYPE.unify(STRING_TYPE), Empty.map());
        assertEquals(INT_TYPE.unify(INT_TYPE), Empty.map());
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

        PMap<TypeVar, Type> mapping = vectorVarType.unify(vectorConcreteType);

        assertEquals(mapping, HashTreePMap.singleton(var, STRING_TYPE));
        assertEquals(vectorConcreteType, vectorVarType.apply(mapping));
    }
}