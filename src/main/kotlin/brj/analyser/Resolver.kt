package brj.analyser

import brj.runtime.*
import brj.runtime.Symbol.Companion.mkSym

internal val CORE_NS = mkSym("brj.core")

internal interface Resolver {
    fun resolveVar(ident: Ident): GlobalVar? = null
    fun resolveTypeAlias(ident: Ident): TypeAlias? = null

    data class NSResolver(val env: RuntimeEnv? = null,
                          val referVars: Map<Symbol, GlobalVar> = emptyMap(),
                          val referTypeAliases: Map<Symbol, TypeAlias> = emptyMap(),
                          val aliases: Map<Symbol, NSEnv> = emptyMap(),
                          val nsEnv: NSEnv? = null) : Resolver {

        private fun resolveNS(ns: Symbol) = aliases[ns] ?: (if (ns == nsEnv?.ns) nsEnv else null) ?: env?.nses?.get(ns)

        override fun resolveVar(ident: Ident): GlobalVar? =
            when (ident) {
                is Symbol -> nsEnv?.vars?.get(ident) ?: referVars[ident] ?: env?.nses?.get(CORE_NS)?.vars?.get(ident)
                is QSymbol -> (resolveNS(ident.ns) ?: TODO("can't find NS")).vars[ident.base]
            }

        override fun resolveTypeAlias(ident: Ident) =
            nsEnv?.typeAliases?.get(ident)
                ?: when (ident) {
                    is Symbol -> nsEnv?.typeAliases?.get(ident) ?: referTypeAliases[ident]
                    is QSymbol -> (resolveNS(ident.ns) ?: TODO("can't find NS")).typeAliases[ident.base]
                }

        companion object {
            fun create(env: RuntimeEnv, nsHeader: NSHeader) =
                NSResolver(env,
                    referVars = nsHeader.refers.filterKeys { it.symbolKind != SymbolKind.TYPE_ALIAS_SYM }.entries.associate {
                        it.key to
                            ((env.nses[it.value.ns] ?: TODO("can't find ${it.value.ns} NS"))
                                .vars[it.value.base] ?: TODO("can't find refer ${it.value}"))
                    },
                    referTypeAliases = nsHeader.refers.filterKeys { it.symbolKind == SymbolKind.TYPE_ALIAS_SYM }.entries.associate {
                        it.key to
                            ((env.nses[it.value.ns] ?: TODO("can't find ${it.value.ns} NS"))
                                .typeAliases[it.value.base] ?: TODO("can't find refer ${it.value}"))
                    },
                    aliases = nsHeader.aliases.entries.associate { (aliasSym, alias) ->
                        aliasSym to (env.nses[alias.ns] ?: TODO("can't find ${alias.ns} NS"))
                    })
        }

    }
}