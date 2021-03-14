package brj.nodes;

import brj.runtime.BridjeVector;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class VectorNode extends CollNode {

    @Override
    @Specialization
    BridjeVector executeColl(Object[] res) {
        return new BridjeVector(res);
    }
}
