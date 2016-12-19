package bridje.runtime;

import bridje.types.Type;
import org.junit.Test;
import org.pcollections.Empty;

import java.time.Instant;
import java.util.Date;

import static bridje.Util.vectorOf;
import static bridje.types.Type.SimpleType.STRING_TYPE;
import static org.junit.Assert.assertEquals;

public class JavaCallTest {
    @Test
    public void findsInstantNow() throws Exception {
        assertEquals(new JavaCall.StaticMethodCall(Instant.class, "now",
                new JavaCall.JavaSignature(Empty.vector(), new JavaCall.JavaReturn(Instant.class, Empty.vector()))),
            JavaCall.StaticMethodCall.find(Instant.class, "now", new Type.JavaType(Instant.class)));
    }

    @Test(expected = JavaCall.NoMatches.class)
    public void doesntFindBadType() throws Exception {
        JavaCall.StaticMethodCall.find(Instant.class, "now", new Type.JavaType(Date.class));
    }

    @Test
    public void findsStringTrim() throws Exception {
        assertEquals(new JavaCall.InstanceMethodCall(String.class, "trim",
                new JavaCall.JavaSignature(Empty.vector(), new JavaCall.JavaReturn(String.class, Empty.vector()))),
            JavaCall.InstanceMethodCall.find(String.class, "trim", new Type.FnType(vectorOf(STRING_TYPE), STRING_TYPE)));
    }
}