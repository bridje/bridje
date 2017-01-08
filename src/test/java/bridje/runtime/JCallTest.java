package bridje.runtime;

import org.junit.Test;
import org.pcollections.Empty;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class JCallTest {
    @Test
    public void findsInstantNow() throws Exception {
        assertEquals(new JCall.StaticMethodCall(Instant.class, "now",
                new JSignature(Empty.vector(), new JSignature.JReturn(Instant.class, Empty.vector()))),
            JCall.StaticMethodCall.find(Instant.class, "now"));
    }

    @Test(expected = JCall.NoMatches.class)
    public void doesntFindBadType() throws Exception {
        JCall.StaticMethodCall.find(Instant.class, "now");
    }

    @Test
    public void findsStringTrim() throws Exception {
        assertEquals(new JCall.InstanceMethodCall(String.class, "trim",
                new JSignature(Empty.vector(), new JSignature.JReturn(String.class, Empty.vector()))),
            JCall.InstanceMethodCall.find(String.class, "trim"));
    }
}