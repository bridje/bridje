package brj.nodes.builtins;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.nodes.ExprNode;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;

public abstract class PolyNode extends ExprNode {
    public PolyNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization
    public Object doExecute(VirtualFrame frame,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        return ctx.getTruffleEnv().parsePublic(Source.newBuilder(((String) frame.getArguments()[1]), ((String) frame.getArguments()[2]), "<brj-inline>").build()).call();
    }
}
