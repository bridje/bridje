package brj

internal sealed class Result<out E, out T> {
    data class Ok<T>(val value: T) : Result<Nothing, T>()
    data class Err<E>(val error: E) : Result<E, Nothing>()

    inline fun <U> flatMap(f: (T) -> Result<@UnsafeVariance E, U>): Result<E, U> = when (this) {
        is Ok -> f(value)
        is Err -> this
    }

    inline fun <U> map(f: (T) -> U): Result<E, U> = flatMap { Ok(f(it)) }
}
