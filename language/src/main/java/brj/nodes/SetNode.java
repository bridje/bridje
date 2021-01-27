package brj.nodes;

import brj.BridjeSet;
import com.oracle.truffle.api.source.SourceSection;

public class SetNode extends CollNode<BridjeSet> {

    public SetNode(ExprNode[] exprs, SourceSection loc) {
        super(exprs, loc);
    }

    @Override
    BridjeSet buildColl(Object[] res) {
        return new BridjeSet(res);
    }
}
