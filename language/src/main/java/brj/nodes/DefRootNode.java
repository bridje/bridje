package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.Typing;
import brj.runtime.Symbol;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "sym", type = Symbol.class)
@NodeField(name = "typing", type = Typing.class)
@NodeChild(value = "expr", type = ExprNode.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class DefRootNode extends RootNode {

    protected DefRootNode(BridjeLanguage lang, FrameDescriptor frameDescriptor) {
        super(lang, frameDescriptor);
    }

    public abstract Symbol getSym();

    public abstract Typing getTyping();

    @NotNull
    @Specialization
    public Object doExecute(VirtualFrame frame,
                            Object exprVal,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        ctx.getBridjeEnv().def(getSym(), getTyping(), exprVal);
        return exprVal;
    }
}
