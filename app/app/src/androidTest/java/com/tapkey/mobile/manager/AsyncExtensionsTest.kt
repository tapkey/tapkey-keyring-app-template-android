/*
 * Copyright (c) 2022 Tapkey GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The software is only used for evaluation purposes OR educational purposes OR
 * private, non-commercial, low-volume projects.
 *
 * The above copyright notice and these permission notices shall be included in all
 * copies or substantial portions of the Software.
 *
 * For any use not covered by this license, a commercial license must be acquired
 * from Tapkey GmbH.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tapkey.mobile.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tapkey.mobile.concurrent.*
import io.tapkey.util.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromiseExtensionsTest {

    @Test
    fun suspendCoroutineWithPromise_shouldHandleCancellationCorrectly() {

        runBlocking {

            var signal1 = CompletableDeferred<Unit>()
            var signal2 = PromiseSource<Unit>(getAsyncScheduler())
            var signal3 = CompletableDeferred<Unit>()

            val job = async {
                suspendCoroutineWithPromise { ct -> Async.first()
                    .continueAsyncOnUi {
                        Async.delayAsync(1)
                    }.continueAsyncOnUi {
                        signal1.complete(Unit)

                        // wait until cancellation was requested.
                        signal2.promise
                    }.continueAsyncOnUi {

                        // We're already cancelled, but we don't listen for the CT, so we should continue.
                        Async.delayAsync(1)
                    }.continueOnUi {
                        signal3.complete(Unit)
                    }.continueAsyncOnUi {
                        Async.loopAsync {
                            Async.delayAsync(10000000, ct).asConst(LoopResult.Continue)
                        }
                    }.asUnit()
                }
            }

            val message = "12345"
            signal1.await()
            job.cancel(message)
            signal2.setResult(Unit)
            signal3.await()

            job.join()
            assert(job.isCancelled)

            try {
                job.await()
                assert(false)
            } catch (e: CancellationException) {
                assert(e.message == message)
            }
        }
    }

    @Test
    fun DeferredAsPromise_shouldHandleCancellationCorrectly() {

        runBlocking {

            var signal = CompletableDeferred<Unit>()

            val deferred = async {
                delay(1)
                signal.complete(Unit)
                delay(1000000)
            }

            val promise = deferred.asPromise()

            signal.await()

            deferred.cancel()

            try {
                promise.await()
                assert(false)
            } catch (e: AsyncException) {
                assert(e.syncSrcException is CancellationException)
            }
        }
    }

    @Test
    fun Cancellable_shouldHandleCancellationCorrectly() {

        val cts = CancellationTokenSource()

        runBlocking {

            var signal = CompletableDeferred<Unit>()

            val job = async {
                try {
                    cancellable(cts.token) {
                        delay(1)
                        signal.complete(Unit)
                        delay(1000000)
                    }

                    assert(false);

                } finally {
                    assert(isActive)
                }
            }

            signal.await()

            val message = "xxx123";
            cts.requestCancellation(message)

            job.join()
            assert(job.isCancelled)

            try {
                job.await()
                assert(false)
            } catch (e: CancellationException) {
                assert(e.message == message)
            }
        }
    }
}
