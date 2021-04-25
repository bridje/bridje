package brj.nodes.builtins;

import brj.BridjeLanguage;
import brj.nodes.ExprNode;
import brj.runtime.BridjeContext;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PolyNode extends ExprNode {
    public PolyNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization
    public Object doExecute(VirtualFrame frame,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        return ctx.poly((String) frame.getArguments()[1], ((String) frame.getArguments()[2]));
    }
}
