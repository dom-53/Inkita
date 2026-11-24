package net.dom53.inkita

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Series detail starts many requests in parallel. When the server is unreachable one of them fails, and a failing
 * `async` reports its exception to the parent coroutine even if the caller catches it around `await()`.
 * Without a supervisor scope that reached the uncaught exception handler, i.e. the app crashed.
 */
class ParallelRequestFailureTest {
    private fun scopeRecordingCrashes(crashes: MutableList<Throwable>) = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> crashes += e })

    private suspend fun failingRequest(): Nothing {
        delay(10)
        throw SocketTimeoutException("failed to connect after 8000ms")
    }

    @Test
    fun withoutASupervisorScopeAFailedRequestEscapesTheTryCatch() =
        runBlocking {
            val crashes = CopyOnWriteArrayList<Throwable>()
            val caught = CopyOnWriteArrayList<Throwable>()
            val load =
                scopeRecordingCrashes(crashes).launch {
                    try {
                        val a = async { failingRequest() }
                        val b = async { delay(5_000) }
                        a.await()
                        b.await()
                    } catch (e: SocketTimeoutException) {
                        caught += e
                    }
                }
            load.join()
            delay(100)
            assertEquals("the caller still sees the failure", 1, caught.size)
            assertTrue("but it also reached the uncaught handler (the crash)", crashes.isNotEmpty())
        }

    @Test
    fun withASupervisorScopeTheFailureStaysInsideTheTryCatch() =
        runBlocking {
            val crashes = CopyOnWriteArrayList<Throwable>()
            val caught = CopyOnWriteArrayList<Throwable>()
            val load =
                scopeRecordingCrashes(crashes).launch {
                    try {
                        supervisorScope {
                            val a = async { failingRequest() }
                            val b = async { delay(5_000) }
                            a.await()
                            b.await()
                        }
                    } catch (e: SocketTimeoutException) {
                        caught += e
                    }
                }
            joinAll(load)
            delay(100)
            assertEquals(1, caught.size)
            assertTrue("no uncaught exception, so no crash: $crashes", crashes.isEmpty())
        }
}
