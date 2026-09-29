---
title: Errors
description: Anomalies, throwing them, and catching them with try.
---

Errors in Bridje are **anomalies**: tags, in `brj.core`, over a record of details.
The categories follow [cognitect.anomalies](https://github.com/cognitect-labs/anomalies) — each says what kind of failure it is, and so what a caller might do about it.

## Categories

`Unavailable`
: The callee isn't available — try again, maybe later.

`Interrupted`
: The operation was interrupted — `brj.concurrent`'s interruption throws this.

`Busy`
: The callee is overloaded — back off and try again.

`Incorrect`
: The caller made a mistake — fix the call.

`Forbidden`
: The caller isn't allowed — fix the permissions.

`Unsupported`
: The operation isn't supported — don't retry.

`NotFound`
: The thing asked for doesn't exist.

`Conflict`
: The request conflicts with the current state — coordinate, then retry.

`Fault`
: The callee has a bug — nothing the caller can do.

`Host`
: An exception thrown by the host — a Java exception.

## Throwing

`throw(anomaly)`
: Throws `anomaly`. `throw`'s return type is `Nothing`, so it fits anywhere a value is expected.

```bridje
throw(NotFound{.exnMessage "no such user"})
```

An anomaly's record can carry any keys; `.exnMessage`, a `Str`, is the conventional one, and becomes the exception's message on the host.

## Catching

`try: body… catch: pattern handler … finally: cleanup…`
: Evaluates the body; if it throws, the first `catch:` pattern that matches the anomaly selects the handler.
  Patterns are as in [`case:`](/reference/records-tags-enums/#pattern-matching), and a final unpaired form is the default.

```bridje
try:
  findUser(id)
  catch: NotFound(e) nil
  catch: Host(e) throw(Fault{.exnMessage "lookup failed"})
  finally: closeConnection()
```

An anomaly's keys are all optional, so a caught anomaly's message is read with `.?exnMessage(e)`.
An anomaly that no pattern matches carries on up the stack.
