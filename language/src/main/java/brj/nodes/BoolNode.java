package brj.nodes;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name="value", type=Boolean.class)
@NodeField(name="loc", type= SourceSection.class)
public abstract class BoolNode extends ExprNode {
    protected abstract boolean getValue();

    @NotNull
    @Specialization
    public Object doExecute(@NotNull VirtualFrame frame) {
        return getValue();
    }
}
