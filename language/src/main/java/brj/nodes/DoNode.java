package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeChild(value = "doExprs", type = ExecuteArrayNode.class)
@NodeChild(value = "doExpr", type = ExprNode.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class DoNode extends ExprNode {

    protected DoNode(BridjeLanguage lang) {
        super(lang);
    }

    @NotNull
    @Specialization
    public Object execute(Object[] doExprs, Object doExpr) {
        return doExpr;
    }
}
