package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeObject;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.source.SourceSection;

@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class RecordNode extends ExprNode {

    @Children
    private final PutMemberNode[] putMemberNodes;

    public RecordNode(BridjeLanguage lang, PutMemberNode[] putMemberNodes) {
        super(lang);
        this.putMemberNodes = putMemberNodes;
    }

    public abstract static class PutMemberNode extends Node {

        private final String key;
        @Child private ExprNode valueNode;

        public PutMemberNode(String key, ExprNode valueNode) {
            this.key = key;
            this.valueNode = valueNode;
        }

        public abstract Object executePut(VirtualFrame frame, BridjeObject obj);

        @Specialization
        public Object doPut(VirtualFrame frame, BridjeObject obj,
                            @CachedLibrary(limit = "3") DynamicObjectLibrary dynObj) {
            Object value = valueNode.execute(frame);
            dynObj.put(obj, key, value);
            return value;
        }
    }

    @Specialization
    @ExplodeLoop
    public Object doExecute(VirtualFrame frame) {
        BridjeObject obj = new BridjeObject();
        for (PutMemberNode putMemberNode : putMemberNodes) {
            putMemberNode.executePut(frame, obj);
        }
        return obj;
    }
}
