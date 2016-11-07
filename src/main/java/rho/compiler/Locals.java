package rho.compiler;

import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.analyser.LocalVar;
import rho.types.ValueType;
import rho.util.Pair;

import static rho.util.Pair.pair;

final class Locals {

    static final class Local {
        final LocalVar<ValueType> localVar;
        final int idx;

        Local(LocalVar<ValueType> localVar, int idx) {
            this.localVar = localVar;
            this.idx = idx;
        }

        @Override
        public String toString() {
            return String.format("(Local %d %s)", idx, localVar);
        }
    }

    final PMap<LocalVar, Local> locals;
    final int nextIdx;

    static Locals staticLocals() {
        return new Locals(Empty.map(), 0);
    }

    private Locals(PMap<LocalVar, Local> locals, int nextIdx) {
        this.locals = locals;
        this.nextIdx = nextIdx;
    }

    Pair<Locals, Local> newLocal(LocalVar<ValueType> localVar) {
        Local local = new Local(localVar, nextIdx);
        return pair(new Locals(locals.plus(localVar, local), nextIdx + Type.getType(localVar.type.javaType()).getSize()), local);
    }
}
