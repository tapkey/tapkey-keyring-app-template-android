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

package io.tapkey.util

import com.tapkey.mobile.concurrent.*
import kotlinx.coroutines.*
import net.tpky.mc.concurrent.AsyncSchedulers
import kotlin.coroutines.*

fun getAsyncScheduler() : AsyncScheduler =
    AsyncSchedulers.current() ?: AsyncSchedulers.fromExecutor(Dispatchers.Default.asExecutor())

fun <T> Promise<T>.register(continuation: Continuation<T>) {
    this.register innerRegister@ { asyncResult ->

        val result: T

        try {
            result = asyncResult.get()
        } catch (e: AsyncException) {
            continuation.resumeWithException(e)
            return@innerRegister
        }

        continuation.resume(result)
    }
}

fun <T> Promise<T>.registerOnCompletion(continuation: Continuation<Unit>) =
    this.register { continuation.resume(Unit) }

suspend fun <T> Promise<T>.await(): T = suspendCoroutineWithPromise { this }

suspend inline fun <T> suspendCoroutineWithPromise(crossinline f: ((CancellationToken) -> Promise<T>)): T {

    val cts = CancellationTokenSource()

    val scheduler = getAsyncScheduler()
    val promise = Async.switchToScheduler(scheduler, scheduler) { f(cts.token) }

    try {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cts.requestCancellation() }
            promise.register(continuation)
        }
    } finally {
        // Has operation completed? If not, the coroutine has probably been cancelled, but according
        // to semantics of Promises, we wait for the promise to complete.
        if (promise.result == null) {
            // A cancelled context cannot be suspended, so we switch to the NonCancellable context,
            // which still allows us to suspend and wait for our promise.
            withContext(NonCancellable) {
                suspendCoroutine<Unit> { continuation ->
                    promise.registerOnCompletion(continuation)
                }
            }
        }
    }
}

fun <T> Deferred<T>.asPromise(): Promise<T> {

    val scheduler = getAsyncScheduler()
    val res = PromiseSource<T>(scheduler)

    this.invokeOnCompletion { cause ->

        if (cause != null) {
            res.setException(cause as? Exception ?: RuntimeException(cause))
        } else {
            res.setResult(getCompleted())
        }
    }

    return res.promise
}

suspend inline fun <T> cancellable(ct: CancellationToken, crossinline block: suspend CoroutineScope.() -> T) : T =
    coroutineScope {
        ct.register { cancel(ct.message) }.use {
            block()
        }
    }

fun <T> Promise<T>.asUnit() : Promise<Unit> =
    this.asConst(Unit)