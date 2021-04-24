package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeKey;
import com.oracle.truffle.api.frame.VirtualFrame;

public class KeywordNode extends ExprNode {
    private final BridjeKey keyword;

    public KeywordNode(BridjeLanguage lang, BridjeKey keyword) {
        super(lang);
        this.keyword = keyword;
    }

    @Override
    public BridjeKey execute(VirtualFrame frame) {
        return keyword;
    }
}
