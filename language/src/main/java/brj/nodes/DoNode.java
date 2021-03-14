package brj.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeChild(value = "doExprs", type = ExecuteArrayNode.class)
@NodeChild(value = "doExpr", type = ExprNode.class)
@NodeField(name = "loc", type = SourceSection.class)
public abstract class DoNode extends ExprNode {

    @NotNull
    @Specialization
    public Object execute(Object[] doExprs, Object doExpr) {
        return doExpr;
    }
}
