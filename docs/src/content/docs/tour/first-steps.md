---
title: First steps
description: Values, calling functions, defining things, and how Bridje's syntax fits together.
---

This tour introduces Bridje a piece at a time, building up to a small to-do list application.
It assumes you've [got a project running](/getting-started/), and that you've programmed before — but not necessarily in a Lisp.

The examples are best tried in a [REPL](/getting-started/#a-repl): send an example over, see what comes back, change it, and send it again.
(For now, each evaluation stands alone, so send an example that defines something together with the code that uses it.)
Where it helps, we'll show the result in a comment:

```bridje
add(1, 2)
// => 3
```

## Values

Bridje has the literals you'd expect:

```bridje
42              // an Int (64-bit)
3.14            // a Double
"hello"         // a Str
true            // a Bool
nil             // nothing at all
```

and three kinds of collection:

```bridje
[1, 2, 3]                    // a vector
#{"a", "b"}                  // a set
{.title "Buy milk"}          // a record — more on these later
```

Commas are optional — they're whitespace, and there to help the reader.
`[1 2 3]` is the same vector.

Values are immutable: nothing in Bridje will change a vector or a record out from under you.

## Calling functions

A function call looks like it does in most languages:

```bridje
add(1, 2)
// => 3

count([1, 2, 3])
// => 3

println("hello")
```

There are no operators: `add`, `sub`, `mul`, `div`, `eq`, `lt` and friends are all ordinary functions.
That might look verbose at first, but it means there's no precedence to remember, and every function — yours included — is called the same way.

## Defining things

`def:` gives a name to a value:

```bridje
decl: answer Int
def: answer 42

answer
// => 42
```

or, with a parameter list, defines a function:

```bridje
decl: double(Int) Int
def: double(x)
  mul(x, 2)

double(21)
// => 42
```

:::note[About those `decl:`s]
Each `decl:` line above a definition shows its type: `double(Int) Int` is a function taking an `Int` and returning one.
They're there so you get used to reading types — Bridje works them out for itself, and you never need to write them.
We'll come back to them [later](/tour/tags-and-enums/#declaring-types).
:::

The body of `double` is indented under the `def:`.
This is the one piece of Bridje that's sensitive to whitespace, so let's look at it properly.

## Blocks

A name followed by a colon — `def:`, `if:`, `let:` — opens a **block**.
The block contains everything after the colon on that line, and every line indented further than the name:

```bridje
decl: describe(Int) Str
def: describe(n)
  if: gt(n, 0)
    "positive"
    "not positive"
```

`if:` takes three things: a condition, what to return if it's true, and what to return if it's false.
The condition sits on the `if:` line; each branch goes on its own line, indented under it.

Everything in Bridje is an expression that returns a value — there are no statements.
`if` returns the value of whichever branch it took, and a function returns the value of its body.

## It's a Lisp

Now for the reveal: Bridje is a Lisp.
Every piece of Bridje code is, underneath, a list — `(operator arg1 arg2 ...)` — and the syntax you've seen so far is a thin layer over that:

```bridje
add(1, 2)          // is (add 1 2)

def: double(x)     // is (def (double x)
  mul(x, 2)        //       (mul x 2))
```

A call `f(a, b)` moves `f` inside the parentheses; a block `f: ...` is an opening parenthesis whose closing one is implied by the indentation.
You can write the parenthesised form wherever you like — it's the same code — but idiomatic Bridje uses the sugared forms for almost everything.

Why does this matter?
Because it's what makes Bridje's code uniform: every expression has the same shape, so any sub-expression can be pulled out, moved or wrapped without a second thought.
It's also what makes [macros](/reference/macros/) possible — functions that take code and return code.

## Next

[Functions](/tour/functions/) — local bindings, anonymous functions, and working with collections.
