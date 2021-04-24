package brj.nodes;

import brj.BridjeLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.source.SourceSection;

@NodeChild(value = "metaObj", type = ExprNode.class)
@NodeChild(value = "params", type = ExecuteArrayNode.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class NewNode extends ExprNode {
    public NewNode(BridjeLanguage lang) {
        super(lang);
    }

    @Specialization(limit = "3")
    public Object doExecute(TruffleObject metaObj,
                            Object[] params,
                            @CachedLibrary("metaObj") InteropLibrary interop) {
        try {
            return interop.instantiate(metaObj, params);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }
}
