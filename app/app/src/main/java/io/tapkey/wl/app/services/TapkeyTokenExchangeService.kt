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
import android.net.Uri
import io.tapkey.wl.app.R
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse


class TapkeyTokenExchangeService(private var context: Context) {

    companion object {
        private val TAG = TapkeyTokenExchangeService::class.qualifiedName
    }

    suspend fun exchangeToken(externalToken: String): TokenResponse {
        val serviceConfig = getAuthorizationServiceConfiguration()
        val tokenRequest = buildTokenRequest(serviceConfig, externalToken)
        return suspendTokenRequest { callback -> AuthorizationService(context).performTokenRequest(tokenRequest, callback) }
    }

    private fun buildTokenRequest(
        serviceConfig: AuthorizationServiceConfiguration,
        externalToken: String
    ): TokenRequest {
        return TokenRequest.Builder(
            serviceConfig,
            context.getString(R.string.tapkey_oauth_client_id)
        )
            .setCodeVerifier(null)
            .setGrantType("http://tapkey.net/oauth/token_exchange")
            .setScopes(hashSetOf("register:mobiles", "read:user", "handle:keys"))
            .setAdditionalParameters(
                hashMapOf(
                    "provider" to context.getString(R.string.tapkey_identity_provider_id),
                    "subject_token_type" to "jwt",
                    "subject_token" to externalToken,
                    "audience" to "tapkey_api",
                    "requested_token_type" to "access_token"
                )
            )
            .build()
    }

    private suspend fun getAuthorizationServiceConfiguration(): AuthorizationServiceConfiguration =
        suspendConfigRequest { cb -> AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(context.getString(R.string.tapkey_authorization_endpoint)), cb) }
}