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

package io.tapkey.wl.app.util

object AnalyticsEvents {

    object Events {
        const val APP_STARTED = "app_started"
        const val RECEIVED_HCE_INTENT = "received_hce_event"
        const val TRIGGER_LOCK_STARTED = "trigger_lock_started"
        const val TRIGGER_LOCK_SUCCEEDED = "trigger_lock_succeeded"
        const val TRIGGER_LOCK_FAILED = "trigger_lock_failed"
        const val TOKEN_REFRESH_FAILED = "token_refresh_failed"
        const val LOGIN_FAILED = "login_failed"
        const val LOGIN_SUCCEEDED = "login_succeeded"
    }

    object Parameters {
        const val TAPKEY_KEYRING_APP_TEMPLATE_VERSION = "tapkey_keyring_app_template_version"
        const val TECHNOLOGY = "technology"
        const val TECHNOLOGY_METHOD = "technology_method"
        const val COMMAND_RESULT_CODE = "command_result_code"
        const val REASON = "reason"
    }

}