package bridje.runtime;

import bridje.types.Type;
import org.junit.Test;
import org.pcollections.Empty;

import java.time.Instant;
import java.util.Date;

import static bridje.Util.vectorOf;
import static bridje.types.Type.SimpleType.STRING_TYPE;
import static org.junit.Assert.assertEquals;

public class JCallTest {
    @Test
    public void findsInstantNow() throws Exception {
        assertEquals(new JCall.StaticMethodCall(Instant.class, "now",
                new JCall.JSignature(Empty.vector(), new JCall.JReturn(Instant.class, Empty.vector()))),
            JCall.StaticMethodCall.find(Instant.class, "now", new Type.JavaType(Instant.class)));
    }

    @Test(expected = JCall.NoMatches.class)
    public void doesntFindBadType() throws Exception {
        JCall.StaticMethodCall.find(Instant.class, "now", new Type.JavaType(Date.class));
    }

    @Test
    public void findsStringTrim() throws Exception {
        assertEquals(new JCall.InstanceMethodCall(String.class, "trim",
                new JCall.JSignature(Empty.vector(), new JCall.JReturn(String.class, Empty.vector()))),
            JCall.InstanceMethodCall.find(String.class, "trim", new Type.FnType(vectorOf(STRING_TYPE), STRING_TYPE)));
    }
}