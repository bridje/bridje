package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeVector;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class VectorNode extends CollNode {

    protected VectorNode(BridjeLanguage lang) {
        super(lang);
    }

    @Override
    @Specialization
    BridjeVector executeColl(Object[] res) {
        return new BridjeVector(res);
    }
}
