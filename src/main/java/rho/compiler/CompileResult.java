package rho.compiler;

import org.pcollections.PSet;

final class CompileResult {
    public final Instructions instructions;
    public final PSet<NewClass> newClasses;

    CompileResult(Instructions instructions, PSet<NewClass> newClasses) {
        this.instructions = instructions;
        this.newClasses = newClasses;
    }
}
