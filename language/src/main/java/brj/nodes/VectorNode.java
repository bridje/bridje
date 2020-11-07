package brj.nodes;

import brj.BridjeVector;
import com.oracle.truffle.api.source.SourceSection;

public class VectorNode extends CollNode<BridjeVector> {

    public VectorNode(ExprNode[] exprs, SourceSection loc) {
        super(exprs, loc);
    }

    @Override
    BridjeVector buildColl(Object[] res) {
        return new BridjeVector(res);
    }
}
