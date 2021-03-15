package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeSet;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class SetNode extends CollNode {

    protected SetNode(BridjeLanguage lang) {
        super(lang);
    }

    @Override
    @Specialization
    BridjeSet executeColl(Object[] res) {
        return new BridjeSet(res);
    }
}
