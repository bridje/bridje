package brj.nodes;

import brj.runtime.BridjeSet;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class SetNode extends CollNode {

    @Override
    @Specialization
    BridjeSet executeColl(Object[] res) {
        return new BridjeSet(res);
    }
}
