package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

public class NowNode extends ExprNode {

    public NowNode(BridjeLanguage lang) {
        super(lang);
    }

    @TruffleBoundary
    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return currentTimeMillis();
    }
}
