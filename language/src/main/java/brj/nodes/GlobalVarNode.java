package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.GlobalVar;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

@NodeField(name = "globalVar", type = GlobalVar.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class GlobalVarNode extends ExprNode {
    public GlobalVarNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization(assumptions = "globalVar.getAssumption().getAssumption()")
    public Object cachedExecute(@Cached("globalVar.getValue()") Object value) {
        return value;
    }
}
