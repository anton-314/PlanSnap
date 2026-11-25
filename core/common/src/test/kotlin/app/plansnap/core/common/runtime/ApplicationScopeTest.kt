package app.plansnap.core.common.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor

class ApplicationScopeTest {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
        }

    @Test
    fun `given a wrapped scope when read then its context is exposed unchanged`() {
        val wrapped = CoroutineScope(Job() + CoroutineName("test"))
        try {
            assertSame(wrapped.coroutineContext, ApplicationScope(wrapped).coroutineContext)
        } finally {
            wrapped.cancel()
        }
    }

    @Test
    fun `given the production scope when created then it runs on the default dispatcher`() {
        val scope = ApplicationScope.create(dispatchers)
        try {
            assertSame(dispatchers.default, scope.coroutineContext[ContinuationInterceptor])
            assertTrue(scope.coroutineContext[Job]!!.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `given the production scope when a child fails then the scope and its siblings keep running`() {
        val scope = ApplicationScope.create(dispatchers)
        try {
            runBlocking {
                val failing = scope.async { throw IllegalStateException("boom") }
                val failure = runCatching { failing.await() }.exceptionOrNull()
                assertTrue(failure is IllegalStateException, failure.toString())

                assertTrue(scope.coroutineContext[Job]!!.isActive)
                assertEquals(42, scope.async { 42 }.await())
            }
        } finally {
            scope.cancel()
        }
    }
}
