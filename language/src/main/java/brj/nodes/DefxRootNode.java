package brj.nodes;

import brj.BridjeContext;
import brj.BridjeLanguage;
import brj.BridjeTypesGen;
import brj.MonoType;
import brj.runtime.BridjeFunction;
import brj.runtime.BridjeVar;
import brj.runtime.Symbol;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

@NodeField(name = "sym", type = Symbol.class)
@NodeField(name = "type", type = MonoType.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class DefxRootNode extends RootNode {

    public DefxRootNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract Symbol getSym();

    public abstract MonoType getType();

    @NotNull
    @Specialization
    public Object doExecute(VirtualFrame frame,
                            @CachedLanguage BridjeLanguage lang,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        CompilerDirectives.transferToInterpreter();
        Symbol sym = getSym();
        BridjeVar defaultImplVar = new BridjeVar(null);
        ctx.getBridjeEnv().defx(sym, getType(),
            new BridjeFunction(
                Truffle.getRuntime().createCallTarget(
                    DefxRootNodeGen.DefxValueRootNodeGen.create(lang, new FrameDescriptor(), sym, defaultImplVar)
                )),
            defaultImplVar);
        return sym;
    }

    public static class FxMap extends DynamicObject implements TruffleObject {
        public FxMap() {
            super(Shape.newBuilder().layout(FxMap.class).build());
        }
    }

    @NodeField(name = "sym", type = Symbol.class)
    @NodeField(name = "defaultImplVar", type = BridjeVar.class)
    public static abstract class DefxValueRootNode extends RootNode {
        protected DefxValueRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Child
        private DynamicObjectLibrary dynObjs = DynamicObjectLibrary.getUncached();

        @Child
        private IndirectCallNode indirectCallNode = Truffle.getRuntime().createIndirectCallNode();

        protected abstract Symbol getSym();
        protected abstract BridjeVar getDefaultImplVar();

        @Specialization(assumptions = "defaultImplVar.getAssumption().getAssumption()")
        public Object doExecute(VirtualFrame frame,
                                @Cached("defaultImplVar.getValue()") Object defaultImpl,
                                @CachedLibrary(value = "defaultImpl") InteropLibrary interops) {
            throw new UnsupportedOperationException();
        }

        @Specialization
        public Object doExecute(VirtualFrame frame) {
            try {
                var fxMap = BridjeTypesGen.expectFxMap(frame.getArguments()[0]);
                var bridjeFunction = BridjeTypesGen.expectBridjeFunction(dynObjs.getOrDefault(fxMap, getSym(), null));
                return indirectCallNode.call(bridjeFunction.getCallTarget(), frame.getArguments());
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }
}
