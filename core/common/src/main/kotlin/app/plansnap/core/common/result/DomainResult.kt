package app.plansnap.core.common.result

/** Outcome of an operation whose expected failures are values instead of exceptions (ARCHITECTURE section 6). */
sealed interface DomainResult<out T> {
    /** Successful outcome carrying [value]. */
    data class Success<out T>(
        val value: T,
    ) : DomainResult<T>

    /** Failed outcome carrying the expected domain [error]. */
    data class Failure(
        val error: DomainError,
    ) : DomainResult<Nothing>
}

/** Transforms a successful value and passes a failure through unchanged. */
inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> =
    when (this) {
        is DomainResult.Success -> DomainResult.Success(transform(value))
        is DomainResult.Failure -> this
    }

/** Chains another fallible operation and passes a failure through unchanged. */
inline fun <T, R> DomainResult<T>.flatMap(transform: (T) -> DomainResult<R>): DomainResult<R> =
    when (this) {
        is DomainResult.Success -> transform(value)
        is DomainResult.Failure -> this
    }

/** Reduces the result to a single value by handling both outcomes. */
inline fun <T, R> DomainResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (DomainError) -> R,
): R =
    when (this) {
        is DomainResult.Success -> onSuccess(value)
        is DomainResult.Failure -> onFailure(error)
    }

/** Returns the successful value, or `null` for a failure. */
fun <T> DomainResult<T>.getOrNull(): T? = (this as? DomainResult.Success)?.value

/** Returns the error of a failure, or `null` for a success. */
fun DomainResult<*>.errorOrNull(): DomainError? = (this as? DomainResult.Failure)?.error
