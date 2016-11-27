package rho.compiler;

import org.objectweb.asm.Type;
import org.pcollections.Empty;
import org.pcollections.PMap;
import rho.analyser.LocalVar;
import rho.util.Pair;

import static rho.util.Pair.pair;

final class Locals {

    static abstract class Local {
        final LocalVar localVar;
        final Class<?> clazz;

        Local(LocalVar localVar, Class<?> clazz) {
            this.localVar = localVar;
            this.clazz = clazz;
        }

        abstract <T> T accept(LocalVisitor<T> visitor);

        interface LocalVisitor<T> {

            T visit(FieldLocal local);

            T visit(VarLocal local);
        }

        static final class VarLocal extends Local {

            final int idx;

            VarLocal(LocalVar localVar, Class<?> clazz, int idx) {
                super(localVar, clazz);
                this.idx = idx;
            }

            @Override
            public String toString() {
                return String.format("(StackLocal %d %s)", idx, localVar);
            }

            @Override
            <T> T accept(LocalVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        static final class FieldLocal extends Local {

            final ClassLike owner;
            final String fieldName;

            FieldLocal(LocalVar localVar, Class<?> clazz, ClassLike owner, String fieldName) {
                super(localVar, clazz);
                this.owner = owner;
                this.fieldName = fieldName;
            }

            @Override
            <T> T accept(LocalVisitor<T> visitor) {
                return visitor.visit(this);
            }

            @Override
            public String toString() {
                return String.format("(FieldLocal %s)", fieldName);
            }
        }
    }

    final PMap<LocalVar, Local> locals;
    final int nextIdx;

    static Locals staticLocals() {
        return new Locals(Empty.map(), 0);
    }

    static Locals instanceLocals() {
        return new Locals(Empty.map(), 1);
    }

    private Locals(PMap<LocalVar, Local> locals, int nextIdx) {
        this.locals = locals;
        this.nextIdx = nextIdx;
    }

    Pair<Locals, Local.VarLocal> newVarLocal(LocalVar localVar, Class<?> clazz) {
        Local.VarLocal local = new Local.VarLocal(localVar, clazz, nextIdx);
        return pair(new Locals(locals.plus(localVar, local), nextIdx + Type.getType(clazz).getSize()), local);
    }

    Pair<Locals, Local> newFieldLocal(LocalVar localVar, Class<?> clazz, ClassLike owner, String fieldName) {
        Local local = new Local.FieldLocal(localVar, clazz, owner, fieldName);
        return pair(new Locals(locals.plus(localVar, local), nextIdx), local);
    }
}
