package brj.nodes;

import brj.BridjeLanguage;
import brj.runtime.BridjeFunction;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

import static brj.nodes.CallNode.CallArgsNode;

@NodeChild(value = "fn", type = ExprNode.class)
@NodeChild(value = "args", type = CallArgsNode.class, executeWith = {"fn"})
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class CallNode extends ExprNode {

    public abstract static class CallArgsNode extends Node {
        @Child
        private ExprNode fxLocalArgNode;
        @Children
        private final ExprNode[] argNodes;

        public CallArgsNode(ExprNode fxLocalArgNode, ExprNode[] argNodes) {
            this.fxLocalArgNode = fxLocalArgNode;
            this.argNodes = argNodes;
        }

        @Specialization
        @ExplodeLoop
        public Object[] doExecute(VirtualFrame frame, BridjeFunction fn) {
            Object[] args = new Object[argNodes.length + 1];
            args[0] = fxLocalArgNode.execute(frame);

            for (int i = 0; i < argNodes.length; i++) {
                args[i + 1] = argNodes[i].execute(frame);
            }

            return args;
        }

        @Specialization
        @ExplodeLoop
        public Object[] doExecute(VirtualFrame frame, TruffleObject fn) {
            Object[] args = new Object[argNodes.length];

            for (int i = 0; i < argNodes.length; i++) {
                args[i] = argNodes[i].execute(frame);
            }
            return args;
        }

        public abstract Object[] execute(VirtualFrame frame, TruffleObject fn);
    }

    protected CallNode(BridjeLanguage lang) {
        super(lang);
    }

    @NotNull
    @Specialization(guards = "fn == cachedFn")
    public Object doExecute(BridjeFunction fn,
                            Object[] args,
                            @Cached("fn") BridjeFunction cachedFn,
                            @Cached("create(cachedFn.getCallTarget())") DirectCallNode callNode) {
        return callNode.call(args);
    }

    @Specialization
    public Object doExecute(Object fn, Object[] args,
                            @CachedLibrary(limit = "3") InteropLibrary interop) {
        try {
            return interop.execute(fn, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.CallTag.class || super.hasTag(tag);
    }
}
