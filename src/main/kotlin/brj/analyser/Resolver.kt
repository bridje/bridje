package brj.analyser

import brj.emitter.Ident
import brj.runtime.GlobalVar
import brj.runtime.TypeAlias

internal interface Resolver {
    fun resolveLocalVar(ident: Ident): GlobalVar?
    fun resolveVar(ident: Ident): GlobalVar?

    fun resolveLocalTypeAlias(ident: Ident): TypeAlias?
    fun resolveTypeAlias(ident: Ident): TypeAlias?
}