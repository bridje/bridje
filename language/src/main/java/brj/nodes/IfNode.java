package brj.nodes;

import brj.BridjeTypesGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IfNode extends ExprNode {

    @Child
    private ExprNode predNode, thenNode, elseNode;

    private final SourceSection loc;

    private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

    public IfNode(ExprNode predNode, ExprNode thenNode, ExprNode elseNode, SourceSection loc) {
        this.predNode = predNode;
        this.thenNode = thenNode;
        this.elseNode = elseNode;
        this.loc = loc;
    }

    @Nullable
    @Override
    public SourceSection getLoc() {
        return loc;
    }

    @NotNull
    @Override
    public Object execute(@NotNull VirtualFrame frame) {
        boolean branch;
        try {
            branch = profile.profile(BridjeTypesGen.expectBoolean(predNode.execute(frame)));
        } catch (UnexpectedResultException e) {
            throw new IllegalStateException(e);
        }

        return branch ? thenNode.execute(frame) : elseNode.execute(frame);
    }
}
