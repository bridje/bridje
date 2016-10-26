package rho.compiler;

import org.pcollections.PSet;
import org.pcollections.PVector;

final class CompileResult {
    public final PVector<Instruction> instructions;
    public final PSet<NewClass> newClasses;

    CompileResult(PVector<Instruction> instructions, PSet<NewClass> newClasses) {
        this.instructions = instructions;
        this.newClasses = newClasses;
    }
}
