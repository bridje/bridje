package brj.nodes;

import brj.BridjeLanguage;
import brj.Typing;
import brj.runtime.BridjeContext;
import brj.runtime.BridjeFunction;
import brj.runtime.BridjeVar;
import brj.runtime.Symbol;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.jetbrains.annotations.NotNull;

import static brj.BridjeTypesGen.expectBridjeFunction;
import static brj.BridjeTypesGen.expectFxMap;

@NodeField(name = "sym", type = Symbol.class)
@NodeField(name = "typing", type = Typing.class)
@NodeField(name = "sourceSection", type = SourceSection.class)
public abstract class DefxRootNode extends RootNode {

    public DefxRootNode(BridjeLanguage lang) {
        super(lang);
    }

    public abstract Symbol getSym();

    public abstract Typing getTyping();

    @NotNull
    @Specialization
    public Object doExecute(VirtualFrame frame,
                            @CachedLanguage BridjeLanguage lang,
                            @CachedContext(BridjeLanguage.class) BridjeContext ctx) {
        CompilerDirectives.transferToInterpreter();
        Symbol sym = getSym();
        BridjeVar defaultImplVar = new BridjeVar(null);
        ctx.defx(sym, getTyping(),
            new BridjeFunction(
                Truffle.getRuntime().createCallTarget(
                    DefxRootNodeGen.DefxValueRootNodeGen.create(lang, new FrameDescriptor(), sym, defaultImplVar)
                )),
            defaultImplVar);
        return sym;
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

        private ConditionProfile useDefaultProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public Object doExecute(VirtualFrame frame) {
            try {
                var fxMap = expectFxMap(frame.getArguments()[0]);
                Object fnValue = null;

                while (fnValue == null && fxMap != null) {
                    fnValue = dynObjs.getOrDefault(fxMap, getSym(), null);
                    fxMap = fxMap.getParent();
                }

                if (useDefaultProfile.profile(fnValue == null)) {
                    fnValue = getDefaultImplVar().getValue();
                }

                return indirectCallNode.call(expectBridjeFunction(fnValue).getCallTarget(), frame.getArguments());
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }
}
