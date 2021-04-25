package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeKey;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

public class CaseNode extends ExprNode {

    public static class CaseMatched extends ControlFlowException {
        final Object res;

        public CaseMatched(Object res) {
            this.res = res;
        }
    }

    public static abstract class CaseClauseNode extends Node {
        abstract void execute(VirtualFrame frame, Object obj) throws CaseMatched;
    }

    public static class NilClauseNode extends CaseClauseNode {

        private final InteropLibrary interop = InteropLibrary.getUncached();
        private final ConditionProfile profile = ConditionProfile.createCountingProfile();

        @Child
        private ExprNode exprNode;

        public NilClauseNode(ExprNode exprNode) {
            this.exprNode = exprNode;
        }

        @Override
        public void execute(VirtualFrame frame, Object obj) throws CaseMatched {
            if (profile.profile(interop.isNull(obj))) {
                throw new CaseMatched(exprNode.execute(frame));
            }
        }
    }

    public static class KeyClauseNode extends CaseClauseNode {

        private InteropLibrary interop = InteropLibrary.getUncached();
        private final ConditionProfile profile = ConditionProfile.createCountingProfile();

        private final BridjeKey key;

        private final FrameSlot frameSlot;

        @Child
        private ExprNode exprNode;

        public KeyClauseNode(BridjeKey key, FrameSlot frameSlot, ExprNode exprNode) {
            this.key = key;
            this.frameSlot = frameSlot;
            this.exprNode = exprNode;
        }

        public void execute(VirtualFrame frame, Object obj) throws CaseMatched {
            try {
                if (profile.profile(interop.isMetaInstance(key, obj))) {
                    frame.setObject(frameSlot, obj);
                    throw new CaseMatched(exprNode.execute(frame));
                }
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
        }
    }

    @Child
    private ExprNode exprNode;

    @Children
    private final CaseClauseNode[] caseClauseNodes;

    @Child
    private ExprNode defaultNode;

    private final SourceSection sourceSection;

    public CaseNode(BridjeLanguage lang,
                    ExprNode exprNode, CaseClauseNode[] caseClauseNodes, ExprNode defaultNode,
                    SourceSection sourceSection) {
        super(lang);
        this.exprNode = exprNode;
        this.caseClauseNodes = caseClauseNodes;
        this.defaultNode = defaultNode;
        this.sourceSection = sourceSection;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        var obj = exprNode.execute(frame);

        for (CaseClauseNode caseClauseNode : caseClauseNodes) {
            try {
                caseClauseNode.execute(frame, obj);
            } catch (CaseMatched caseMatched) {
                return caseMatched.res;
            }
        }

        return defaultNode.execute(frame);
    }
}
