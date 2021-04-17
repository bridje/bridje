package brj.nodes.builtins;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.nodes.ExprNode;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class JClassNode extends ExprNode {
    public JClassNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization
    public Object doExecute(VirtualFrame frame,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        return ctx.getTruffleEnv().lookupHostSymbol(((String) frame.getArguments()[1]));
    }
}
