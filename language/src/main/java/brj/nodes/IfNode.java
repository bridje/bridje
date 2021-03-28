package brj.nodes;

import brj.BridjeLanguage;
import brj.BridjeTypesGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

public class IfNode extends ExprNode {

    @Child
    private ExprNode predNode, thenNode, elseNode;

    private final SourceSection sourceSection;

    private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

    public IfNode(BridjeLanguage lang, ExprNode predNode, ExprNode thenNode, ExprNode elseNode, SourceSection sourceSection) {
        super(lang);
        this.predNode = predNode;
        this.thenNode = thenNode;
        this.elseNode = elseNode;
        this.sourceSection = sourceSection;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @NotNull
    @Override
    public Object execute(@NotNull VirtualFrame frame) {
        boolean branch;
        try {
            branch = profile.profile(BridjeTypesGen.expectBoolean(predNode.execute(frame)));
        } catch (UnexpectedResultException e) {
            throw shouldNotReachHere(e);
        }

        return branch ? thenNode.execute(frame) : elseNode.execute(frame);
    }
}
