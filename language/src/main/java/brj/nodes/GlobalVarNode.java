package brj.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GlobalVarNode extends ExprNode {
    private final Object value;
    private final SourceSection loc;

    public GlobalVarNode(Object value, SourceSection loc) {
        this.value = value;
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
        return value;
    }
}
