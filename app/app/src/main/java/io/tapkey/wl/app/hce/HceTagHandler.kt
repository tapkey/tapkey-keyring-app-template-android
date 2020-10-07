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

package io.tapkey.wl.app.hce

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.*
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.tapkey.mobile.TapkeyServiceFactory
import com.tapkey.mobile.concurrent.CancellationTokens
import com.tapkey.mobile.model.CommandResult
import com.tapkey.mobile.model.CommandResult.CommandResultCode
import io.tapkey.util.MessageResolver
import io.tapkey.util.await
import io.tapkey.wl.app.R
import io.tapkey.wl.app.util.AnalyticsEvents
import io.tapkey.wl.app.util.NotificationIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.tpky.mc.nfc.TkTag
import net.tpky.mc.time.ServerClock
import net.tpky.mc.tlcp.model.TransportChannel
import net.tpky.mc.utils.*
import net.tpky.nfc.NdefConnection
import net.tpky.nfc.TkTagTechnology

/**
 * Utility methods for handling tags that have been received via the `TapkeyHceService`. Tries to
 * dispatch the tag to foreground listeners, if any are present, and initiates a triggerLock
 * operation otherwise.
 */
class HceTagHandler(
    private val app: Context,
    private val tapkeyServiceFactory: TapkeyServiceFactory,
    private val clock: ServerClock
) {

    private val VIBRATE_PATTERN_START = longArrayOf(0, 80)
    private val VIBRATE_PATTERN_SUCCESS = longArrayOf(0, 500)
    private val VIBRATE_PATTERN_FAILURE = longArrayOf(0, 200, 200, 200, 200, 200)


    private val currentTagId: String? = null
    private val currentPendingCommand: PendingTlcpCommand? = null
    private val messageResolver = MessageResolver(app)

    fun handleHceIntent(intent: Intent?) {

        Firebase.analytics.logEvent(AnalyticsEvents.Events.RECEIVED_HCE_INTENT, null)

        try {
            if (!isInteractive(app)) {
                Log.d(TAG, "Received HCE intent, but this phone is not interactive; ignoring.")
                return
            }
            val ndef = NfcUtils.getNdefConnection(intent) ?: return

            GlobalScope.launch(Dispatchers.Main) {
                handleNewLockLocally(ndef)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error while handling new HCE intent.", e)
        }
    }

    private fun triggerLock(lock: NdefConnection): PendingTlcpCommand {
        val tlcpConnection =
            LockConnectionUtils.getTlcpConnection(clock, lock, TransportChannel.NFC_HCE)

        // make sure, disconnection is requested when communication finished, to let the lock
        // save power or do whatever is necessary.
        val promise = ConnectionUtils.runAndDisconnectAsync(
            lock
        ) {
            tapkeyServiceFactory.commandExecutionFacade.triggerLockAsync(
                tlcpConnection,
                CancellationTokens.None
            )
        }
        return PendingTlcpCommand(tlcpConnection, promise)
    }

    private suspend fun handleNewLockLocally(lock: NdefConnection) {

        val analyticsBundle = Bundle()
        analyticsBundle.putString(AnalyticsEvents.Parameters.TECHNOLOGY, "nfc")
        analyticsBundle.putString(AnalyticsEvents.Parameters.TECHNOLOGY_METHOD, "hce")
        Firebase.analytics.logEvent(AnalyticsEvents.Events.TRIGGER_LOCK_STARTED, analyticsBundle)

        val tlcpExecution = triggerLock(lock)
        val builder = NotificationCompat.Builder(
            app,
            NotificationIds.NOTIFICATION_CHANNEL_ID_TriggerLock
        )
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(app.getString(R.string.locking))
            .setContentText(app.getString(R.string.locking_unlocking))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(1, 0, true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setTimeoutAfter(60000)

        val notificationManager = NotificationManagerCompat.from(app)
        notificationManager.notify(NotificationIds.NOTIFICATION_ID_TriggerLock, builder.build())

        val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN_START, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        var commandResult: CommandResult? = null
        var errorMsg: String? = null

        try {
            commandResult = tlcpExecution.promise.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error while triggering lock.", e)
            errorMsg = messageResolver.getMessage(e)
        }

        var success = false
        if (commandResult != null) {

            val resultCode = commandResult.commandResultCode
            if (resultCode == CommandResultCode.Ok) {
                Log.i(TAG, "Trigger lock result: !$resultCode")
                success = true
            } else {
                Log.w(TAG, "Trigger lock result: !$resultCode")
                errorMsg = messageResolver.getMessage(commandResult.commandResultCode)
            }
        }

        val vibrationPattern: LongArray

        if (success) {

            Firebase.analytics.logEvent(AnalyticsEvents.Events.TRIGGER_LOCK_SUCCEEDED, analyticsBundle)

            val headsUpView =
                RemoteViews(app.packageName, R.layout.notification__trigger_lock__success)
            builder
                .setContentText(app.getString(R.string.ok))
                .setCustomContentView(headsUpView)
                .setColor(ContextCompat.getColor(app, R.color.green1_normal))
                .setColorized(true)

            vibrationPattern = VIBRATE_PATTERN_SUCCESS
        } else {

            analyticsBundle.putString(AnalyticsEvents.Parameters.COMMAND_RESULT_CODE, commandResult?.commandResultCode?.name)
            Firebase.analytics.logEvent(AnalyticsEvents.Events.TRIGGER_LOCK_FAILED, analyticsBundle)

            builder
                .setContentText(errorMsg)
                .setCategory(NotificationCompat.CATEGORY_ERROR)

            vibrationPattern = VIBRATE_PATTERN_FAILURE
        }
        builder
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .setTimeoutAfter(5000)
        notificationManager.notify(
            NotificationIds.NOTIFICATION_ID_TriggerLock,
            builder.build()
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(vibrationPattern, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    companion object {
        private val TAG = HceTagHandler::class.java.simpleName
        private fun isInteractive(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isInteractive
        }

        private fun createNotificationChannel(context: Context) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notification_channel_name)
                val description = context.getString(R.string.notification_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(
                    NotificationIds.NOTIFICATION_CHANNEL_ID_TriggerLock,
                    name,
                    importance
                )
                channel.description = description
                channel.setSound(null, AudioAttributes.Builder().build())
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(
                    NotificationManager::class.java
                )
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    init {
        NfcUtils.addTechnologyResolver { tag: TkTag?, technology: Int? ->
            if (tag == null || technology == null || technology != TkTagTechnology.PENDING_TLCP_COMMAND) return@addTechnologyResolver null
            if (currentTagId == null || currentTagId != tag.tagId) return@addTechnologyResolver null
            currentPendingCommand
        }
        createNotificationChannel(app)
    }
}