package brj.nodes.builtins;

import brj.BridjeLanguage;
import brj.nodes.ExprNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ZeroNode extends ExprNode {

    public ZeroNode(BridjeLanguage lang) {
        super(lang);
    }

    public Object execute(VirtualFrame frame) {
        return ((int) frame.getArguments()[1]) == 0;
    }
}
