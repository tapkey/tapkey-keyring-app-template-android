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

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.annotation.RequiresPermission
import com.tapkey.mobile.concurrent.AsyncException
import com.tapkey.mobile.error.TkException
import com.tapkey.mobile.model.CommandResult
import com.tapkey.mobile.model.TkErrorDescriptor
import io.tapkey.wl.app.R
import net.tpky.mc.ble.BleException
import net.tpky.mc.ble.ConnectionLostException
import net.tpky.mc.manager.ServerCommunicationException
import net.tpky.mc.model.ValidityError
import net.tpky.mc.utils.Log
import java.io.IOException

class MessageResolver(private val context: Context) {

    enum class Severity {
        Log,
        Warning,
        Error
    }

    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")
    fun getMessage(commandResultCode: CommandResult.CommandResultCode): String {
        return when (commandResultCode) {
            CommandResult.CommandResultCode.Ok -> context.getString(R.string.success)
            CommandResult.CommandResultCode.WrongLockMode -> context.getString(R.string.wrong_lock_mode)
            CommandResult.CommandResultCode.LockVersionTooOld -> context.getString(R.string.lock_version_too_old_error)
            CommandResult.CommandResultCode.LockVersionTooYoung -> context.getString(R.string.lock_version_too_young_error)
            CommandResult.CommandResultCode.LockNotFullyAssembled -> context.getString(R.string.lock_not_fully_assembled)
            CommandResult.CommandResultCode.ServerCommunicationError -> if (isNetworkOnline()) context.getString(R.string.server_communication_error) else context.getString(R.string.server_communication_error_offline)
            CommandResult.CommandResultCode.LockDateTimeInvalid -> context.getString(R.string.lock_date_time_invalid_error)
            CommandResult.CommandResultCode.TemporarilyUnauthorized -> context.getString(R.string.unauthorized_not_yet_valid_error)
            CommandResult.CommandResultCode.Unauthorized_NotYetValid -> context.getString(R.string.unauthorized_not_yet_valid_error)
            CommandResult.CommandResultCode.Unauthorized -> context.getString(R.string.unauthorized_error)
            CommandResult.CommandResultCode.LockCommunicationError -> context.getString(R.string.lock_communication_error)
            CommandResult.CommandResultCode.UserSpecificError -> context.getString(R.string.generic_error)
            CommandResult.CommandResultCode.TechnicalError -> context.getString(R.string.generic_error)
            else -> context.getString(R.string.generic_error)
        }
    }

    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")
    fun getMessage(exception: java.lang.Exception?): String {
        var e = exception
        if (exception is AsyncException) {
            e = exception.syncSrcException
        }

        if (e is TkException) return getMessage(e)

        if (e is ConnectionLostException) return context.getString(R.string.connection_lost)
        if (e is BleException) return context.getString(R.string.ble_connection_err)
        if (e is ServerCommunicationException) return getMessage(CommandResult.CommandResultCode.ServerCommunicationError)
        if (e is IOException) return unknownMessage()

        return unknownMessage()
    }

    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")
    fun getMessage(errorCode: String, tkErrorDescriptor: TkErrorDescriptor): String {
        val validityError: ValidityError? = try {
            ValidityError.valueOf(errorCode)
        } catch (ignore: IllegalArgumentException) {
            null
        }

        if (validityError == ValidityError.UnexpectedLockResponseError) {
            val responseCode = getResponseCode(tkErrorDescriptor)
            if (responseCode != null) {
                val message = getMessageForResponseCode(responseCode)
                if (message != null) return message
            }
        }

        return validityError?.let { getMessage(it) } ?: unknownMessage()
    }

    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")
    fun getMessage(error: ValidityError): String {
        return when (error) {
            ValidityError.Ok -> context.getString(R.string.ok)
            ValidityError.NfcTransportError -> context.getString(R.string.nfc_transport_error)
            ValidityError.ConcurrencyError -> context.getString(R.string.concurrency_error)
            ValidityError.LockProtocolError -> context.getString(R.string.lock_protocol_error)
            ValidityError.UnexpectedLockResponseError -> context.getString(R.string.unexpected_lock_response_error)
            ValidityError.IllegalLockState -> context.getString(R.string.illegal_lock_state)
            ValidityError.WrongLockMode -> context.getString(R.string.wrong_lock_mode)
            ValidityError.LockFwTooOld -> context.getString(R.string.lock_version_too_old_error)
            ValidityError.DifferentDevice -> context.getString(R.string.different_device)
            ValidityError.SessionBroken -> context.getString(R.string.session_broken)
            ValidityError.NetworkRelatedError -> if (isNetworkOnline()) context.getString(R.string.network_error) else context.getString(
                R.string.network_error_offline
            )
            ValidityError.ServerSideError -> context.getString(R.string.server_side_error)
            ValidityError.LockCommunicationError -> context.getString(R.string.lock_communication_error)
            ValidityError.TransportProtocolError -> context.getString(R.string.transport_protocol_error)
            ValidityError.Generic -> unknownMessage()
            else -> unknownMessage()
        }
    }

    private fun getResponseCode(err: TkErrorDescriptor?): String? {
        if (err == null) return null
        if (err.errorDetails !is Map<*, *>) return null
        val errorDetails = err.errorDetails as Map<*, *>
        val res = errorDetails["responseCode"]
        return res?.toString()
    }

    private fun getMessageForResponseCode(responseCode: String?): String? {
        return if (responseCode == null) null else when (responseCode) {
            "NotFullyAssembled" -> context.getString(R.string.lock_not_fully_assembled)
            else -> null
        }
    }

    private fun unknownMessage(): String {
        return context.getString(R.string.generic_error)
    }

    fun getSeverity(commandResultCode: CommandResult.CommandResultCode): Severity {
        return when (commandResultCode) {
            CommandResult.CommandResultCode.Ok -> Severity.Log
            CommandResult.CommandResultCode.WrongLockMode -> Severity.Warning
            CommandResult.CommandResultCode.LockVersionTooOld -> Severity.Warning
            CommandResult.CommandResultCode.LockVersionTooYoung -> Severity.Warning
            CommandResult.CommandResultCode.LockNotFullyAssembled -> Severity.Error
            CommandResult.CommandResultCode.ServerCommunicationError -> Severity.Error
            CommandResult.CommandResultCode.LockDateTimeInvalid -> Severity.Warning
            CommandResult.CommandResultCode.TemporarilyUnauthorized -> Severity.Warning
            CommandResult.CommandResultCode.Unauthorized_NotYetValid -> Severity.Warning
            CommandResult.CommandResultCode.Unauthorized -> Severity.Warning
            CommandResult.CommandResultCode.LockCommunicationError -> Severity.Error
            CommandResult.CommandResultCode.UserSpecificError -> Severity.Error
            CommandResult.CommandResultCode.TechnicalError -> Severity.Error
            else -> Severity.Error
        }
    }

    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")
    fun isNetworkOnline(): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var netInfo = cm.getNetworkInfo(0)
            if (netInfo != null && netInfo.state == NetworkInfo.State.CONNECTED) return true else {
                netInfo = cm.getNetworkInfo(1)
                if (netInfo != null && netInfo.state == NetworkInfo.State.CONNECTED) return true
            }
        } catch (e: Exception) {
            Log.d(this.javaClass.name, "Can't load ConnectivityManager", e)
        }
        return false
    }
    
}