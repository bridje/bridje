package brj.nodes.builtins;

import brj.BridjeLanguage;
import brj.nodes.ExprNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class DecNode extends ExprNode {

    public DecNode(BridjeLanguage lang) {
        super(lang);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return ((int) frame.getArguments()[1]) - 1;
    }
}
