package brj.nodes;

import brj.runtime.BridjeFunction;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FnNode extends ExprNode {
    private final BridjeFunction fn;
    private final SourceSection loc;

    public FnNode(BridjeFunction fn, SourceSection loc) {
        this.fn = fn;
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
        return fn;
    }
}
