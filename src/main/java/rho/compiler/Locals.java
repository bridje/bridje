package rho.compiler;

import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.analyser.LocalVar;
import rho.util.Pair;

import static rho.util.Pair.pair;

final class Locals {

    final class Local {
        final LocalVar localVar;
        final int idx;

        Local(LocalVar localVar, int idx) {
            this.localVar = localVar;
            this.idx = idx;
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

    Pair<Locals, Local> newLocal(LocalVar localVar) {
        Local local = new Local(localVar, nextIdx);
        return pair(new Locals(locals.plus(localVar, local), nextIdx + 1), local);
    }
}
