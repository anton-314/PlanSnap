package app.plansnap.core.data.repository

import android.database.sqlite.SQLiteFullException
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.StorageProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs [block] and turns every technical failure into `DomainError.Storage` with [problem], or `NO_SPACE` if the
 * device is full. A cancellation of the calling coroutine is rethrown. A cancellation that Room raises because its
 * database was closed does not cancel the caller and is a failure like any other.
 */
@Suppress("TooGenericExceptionCaught") // Every storage, codec or mapping exception must become a value.
internal suspend inline fun <T> storageResult(
    problem: StorageProblem,
    block: () -> DomainResult<T>,
): DomainResult<T> =
    try {
        block()
    } catch (_: SQLiteFullException) {
        DomainResult.Failure(DomainError.Storage(StorageProblem.NO_SPACE))
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
        DomainResult.Failure(DomainError.Storage(problem))
    } catch (_: Exception) {
        DomainResult.Failure(DomainError.Storage(problem))
    }
