package brj.nodes;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

public class LocalVarNode extends ExprNode {
    private final FrameSlot frameSlot;
    private final SourceSection loc;

    public LocalVarNode(FrameSlot frameSlot, SourceSection loc) {
        this.frameSlot = frameSlot;
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
        try {
            return frame.getObject(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw shouldNotReachHere(e);
        }
    }
}
