package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ReadArgNode extends ExprNode {
    private final int idx;

    public ReadArgNode(BridjeLanguage lang, int idx) {
        super(lang);
        this.idx = idx;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return frame.getArguments()[idx];
    }
}
