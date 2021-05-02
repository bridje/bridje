package brj.nodes;

import brj.BridjeLanguage;
import brj.BridjeTypes;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.ExecutableNode;

@TypeSystemReference(BridjeTypes.class)
@GenerateWrapper
public abstract class ExprNode extends ExecutableNode implements InstrumentableNode {

    protected ExprNode(BridjeLanguage lang) {
        super(lang);
    }

    protected ExprNode(ExprNode copy) {
        this(copy.getLanguage(BridjeLanguage.class));
    }

    @Override
    public boolean isInstrumentable() {
        return true;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new ExprNodeWrapper(this, this, probe);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.ExpressionTag.class;
    }
}
