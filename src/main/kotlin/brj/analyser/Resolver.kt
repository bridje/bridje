package brj.analyser

import brj.emitter.Ident
import brj.runtime.GlobalVar
import brj.runtime.TypeAlias

internal interface Resolver {
    fun resolveVar(ident: Ident): GlobalVar?
    fun resolveTypeAlias(ident: Ident): TypeAlias?
}