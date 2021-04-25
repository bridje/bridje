package brj.nodes;

import brj.BridjeLanguage;
import brj.BridjeTypes;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.ExecutableNode;

@TypeSystemReference(BridjeTypes.class)
@GenerateWrapper
public abstract class ExprNode extends ExecutableNode implements InstrumentableNode {
    protected ExprNode() {
        super(null);
    }

    protected ExprNode(BridjeLanguage lang) {
        super(lang);
    }

    @Override
    public boolean isInstrumentable() {
        return true;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new ExprNodeWrapper(this, probe);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.ExpressionTag.class;
    }
}
