package brj.nodes;

import brj.BridjeLanguage;
import brj.BridjeTypesGen;
import brj.runtime.FxMap;
import brj.runtime.Symbol;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

public class WithFxNode extends ExprNode {

    @Child
    private WriteLocalNode writeFxMap;

    @Child
    private ExprNode bodyNode;

    public WithFxNode(BridjeLanguage lang, WriteLocalNode writeFxMap, ExprNode bodyNode) {
        super(lang);
        this.writeFxMap = writeFxMap;
        this.bodyNode = bodyNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        writeFxMap.execute(frame);
        return bodyNode.execute(frame);
    }

    public static class WithFxBindingNode extends Node {

        private final Symbol sym;

        @Child
        private ExprNode exprNode;

        @Child
        private DynamicObjectLibrary dynObjs = DynamicObjectLibrary.getUncached();

        public WithFxBindingNode(Symbol sym, ExprNode exprNode) {
            this.sym = sym;
            this.exprNode = exprNode;
        }

        public void execute(VirtualFrame frame, FxMap fxMap) {
            dynObjs.put(fxMap, sym, exprNode.execute(frame));
        }
    }

    public static class NewFxNode extends ExprNode {
        @Child
        private ExecutableNode oldFxNode;

        @Children
        private final WithFxBindingNode[] bindingNodes;

        public NewFxNode(BridjeLanguage lang, ExecutableNode oldFxNode, WithFxBindingNode[] bindingNodes) {
            super(lang);
            this.oldFxNode = oldFxNode;
            this.bindingNodes = bindingNodes;
        }

        @ExplodeLoop
        public FxMap execute(VirtualFrame frame) {
            final FxMap oldFx;
            try {
                oldFx = BridjeTypesGen.expectFxMap(oldFxNode.execute(frame));
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }

            var newFx = new FxMap(FxMap.Companion.getDEFAULT_SHAPE(), oldFx);

            for (WithFxBindingNode bindingNode : bindingNodes) {
                bindingNode.execute(frame, newFx);
            }

            return newFx;
        }
    }
}
