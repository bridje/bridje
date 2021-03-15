package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

@NodeField(name = "frameSlot", type = FrameSlot.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class LocalVarNode extends ExprNode {
    public LocalVarNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract FrameSlot getFrameSlot();

    @NotNull
    @Specialization
    public Object doExecute(@NotNull VirtualFrame frame) {
        try {
            return frame.getObject(getFrameSlot());
        } catch (FrameSlotTypeException e) {
            throw shouldNotReachHere(e);
        }
    }
}
