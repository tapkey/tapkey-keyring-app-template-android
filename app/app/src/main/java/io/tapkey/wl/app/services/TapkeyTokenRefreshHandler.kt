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

package io.tapkey.wl.app.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.tapkey.mobile.auth.AuthenticationHandlerErrorCodes
import com.tapkey.mobile.auth.KotlinTokenRefreshHandler
import com.tapkey.mobile.error.TkException
import io.tapkey.wl.app.ui.login.LoginActivity
import io.tapkey.wl.app.util.AnalyticsEvents
import kotlinx.coroutines.tasks.await
import java.io.IOException

class TapkeyTokenRefreshHandler(private val context: Context) : KotlinTokenRefreshHandler() {

    private val tokenExchangeManager: TapkeyTokenExchangeService =
        TapkeyTokenExchangeService(context)

    override suspend fun refreshAuthentication(userId: String): String {

        if (FirebaseAuth.getInstance().currentUser == null) {
            throw TkException(AuthenticationHandlerErrorCodes.TokenRefreshFailed)
        }

        val currentUser = FirebaseAuth.getInstance().currentUser ?: throw IOException("no user logged in")

        val firebaseToken = currentUser.getIdToken(false).await()
        val tapkeyToken = tokenExchangeManager.exchangeToken(firebaseToken.token!!).accessToken

        return tapkeyToken!!
    }

    override fun onRefreshFailed(tapkeyUserId: String) {

        Firebase.analytics.logEvent(AnalyticsEvents.Events.TOKEN_REFRESH_FAILED, null)

        /*
         * This sample app does not support multiple Tapkey users, hence the user ID is ignored. It
         * is good practice to check if it matches the user that is expected nonetheless in real
         * applications.
         * Furthermore, if your application is likely unable to recover from this situation without
         * running the user through the application's own authentication logic, this is a good place
         * to force-logout the user, for instance, in this sample:
         * AuthStateManager.setLoggedOut(this).
         */
        Log.d(TAG, "Refreshing Tapkey authentication failed. Redirecting to login activity.")
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(context, LoginActivity::class.java)
        context.startActivity(intent)
    }

    companion object {
        private val TAG = TapkeyTokenRefreshHandler::class.java.simpleName
    }
}