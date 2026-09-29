---
title: Macros and quoting
description: Forms, quote and syntax-quote, unquoting, gensyms, and defining macros.
---

## Forms

The reader turns source into **forms**, and quoting turns code into forms as values.
A form is a value of the enum `brj.rdr/Form`, whose variants are tags in `brj.rdr`:

| Tag | Keys | Read from |
| :-- | :-- | :-- |
| `SymbolForm` | `.sym` | `foo` |
| `QSymbolForm` | `.ns`, `.member` | `c/spawn` |
| `DotSymbolForm` | `.sym` | `.name` |
| `QDotSymbolForm` | `.ns`, `.member` | `m/.name` |
| `List` | `.els` | `(a b)`, `f(a, b)`, `f: a b` |
| `Vector` | `.els` | `[a b]` |
| `Set` | `.els` | `#{a b}` |
| `Record` | `.els` | `{.k v}` |
| `Int`, `Double`, `String`, `BigInt`, `BigDec` | `.value` | literals |

`.sym`, `.ns` and `.member` hold `Symbol`s; `.els` holds a `[Form]`.
Forms carry their source location as metadata.

Forms are sugar-free: `f(a, b)`, `(f a b)` and `f: a b` all read as the same `List`.

A form can be constructed like any other tag, with `brj.rdr` required: `rdr/Int{.value 1}`, `rdr/List{.els [...]}`.
`rdr/fromStr(s)` reads a string into a `[Form]`; `rdr/fromFile(file)` reads a file.

## Quoting

`'form`
: The form itself, unevaluated.
  Symbols stay as written: `'foo` is `SymbolForm{.sym foo}`.

`` `form ``
: The form, with each symbol resolved to the var it refers to: `` `count `` is `brj.core/count`, `` `f/bar `` follows the alias `f`.
  Special-form names stay bare, as do gensyms.
  Locals and host classes can't be resolved — `Cannot resolve local in squote: x (did you mean ~x?)`.
  Use it so that a macro's expansion means the same thing in whichever namespace it's expanded.

`~x`
: Inside either quote, the value of `x`, which must be a `Form`, in place.

`~@xs`
: Inside either quote, each element of `xs`, a `[Form]`, in place.

Outside a quote, `~` and `~@` are errors.

## Gensyms

`name#`
: Inside a quote, a symbol ending in `#` becomes a fresh, unique symbol; every `name#` in one quote is the same one.

```bridje
'(let [tmp# 1] tmp#)
// (let [tmp__5 1] tmp__5)
```

`gensym()` makes one at runtime.

## Defining macros

`defmacro: name(p1, p2, & rest) body…`
: Defines a macro.
  When `name(…)` is analysed, the macro is called with its arguments as unevaluated forms — `rest` collects the remainder as a `[Form]` — and the form it returns is analysed in the call's place.

```bridje
defmacro: when(cond, & body)
  '(if ~cond (do ~@body) nil)

defmacro: unlessLet(bindings, else, then)
  '(ifLet ~bindings ~then ~else)
```

A macro takes its arguments apart with `case:`, over the `brj.rdr` tags:

```bridje
case: step
  rdr/List{els} '(~first(els) v# ~@rest(els))
  bare '(~bare v#)
```

A `case` over a form needs a default, or all thirteen variants.

A macro is a function over forms: its parameters are `Form`s, a rest parameter is a `[Form]`, and its result is a `Form`.

Expansion is limited to a depth of 100: `Maximum macro expansion depth (100) exceeded`.
