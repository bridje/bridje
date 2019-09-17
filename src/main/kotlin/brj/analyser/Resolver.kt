package brj.analyser

import brj.emitter.Ident
import brj.emitter.Symbol
import brj.runtime.GlobalVar
import brj.runtime.TypeAlias
import brj.types.Type

internal interface Resolver {
    fun resolveVar(ident: Ident): GlobalVar?
    fun resolveTypeAlias(ident: Ident): TypeAlias?
    fun expectedType(sym: Symbol): Type?
}