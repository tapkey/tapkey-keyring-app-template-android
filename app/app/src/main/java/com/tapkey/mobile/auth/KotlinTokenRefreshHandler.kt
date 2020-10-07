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

package com.tapkey.mobile.auth

import com.tapkey.mobile.concurrent.CancellationToken
import com.tapkey.mobile.concurrent.CancellationTokens
import com.tapkey.mobile.concurrent.Promise
import io.tapkey.util.asPromise
import io.tapkey.util.cancellable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

public abstract class KotlinTokenRefreshHandler : TokenRefreshHandler {

    final override fun refreshAuthenticationAsync(
        userId: String?, cancellationToken: CancellationToken?
    ): Promise<String> =
        // we use the GlobalScope here, because we don't have access to any other. But we handle
        // cancellation independently of the scope, so this shouldn't cause any issues.
        GlobalScope.async {
            cancellable(cancellationToken ?: CancellationTokens.None) {

                // userId must not be null
                refreshAuthentication(userId!!)
            }
        }.asPromise()

    abstract suspend fun refreshAuthentication(userId: String): String
}