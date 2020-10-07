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

package io.tapkey.wl.app

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.tapkey.mobile.TapkeyAppContext
import com.tapkey.mobile.TapkeyEnvironmentConfigBuilder
import com.tapkey.mobile.TapkeyServiceFactory
import com.tapkey.mobile.TapkeyServiceFactoryBuilder
import com.tapkey.mobile.ble.TapkeyBleAdvertisingFormatBuilder
import com.tapkey.mobile.broadcast.PollingScheduler
import com.tapkey.mobile.fcm.FirebasePushNotificationTokenProvider
import io.sentry.android.core.SentryAndroid
import io.tapkey.wl.app.hce.HceTagHandler
import io.tapkey.wl.app.services.TapkeyTokenRefreshHandler
import io.tapkey.wl.app.util.AnalyticsEvents
import net.tpky.mc.service.TapkeyHceService
import net.tpky.mc.time.ServerClock


class App : Application(), TapkeyAppContext {

    private var tapkeyServiceFactory: TapkeyServiceFactory? = null

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        val defaultAnalyticsParameters = Bundle()
        defaultAnalyticsParameters.putString(AnalyticsEvents.Parameters.TAPKEY_KEYRING_APP_TEMPLATE_VERSION, getString(R.string.tapkey_keyring_app_template_version))
        Firebase.analytics.setDefaultEventParameters(defaultAnalyticsParameters)

        val sentryDsn = resources.getString(R.string.sentry_dsn)
        if (sentryDsn.isNotEmpty()) {
            SentryAndroid.init(this) { options ->
                options.setBeforeSend { event, _ ->
                    event.setExtra(AnalyticsEvents.Parameters.TAPKEY_KEYRING_APP_TEMPLATE_VERSION, getString(R.string.tapkey_keyring_app_template_version))
                    event
                }
            }
        }

        val serverClock = ServerClock()

        val tapkeyBleAdvertisingFormatBuilder = TapkeyBleAdvertisingFormatBuilder()
            .addV2Format(resources.getInteger(R.integer.tapkey_domain_id))

        val v1BleServiceUuid = resources.getString(R.string.tapkey_v1_ble_service_uuid)
        if(!TextUtils.isEmpty(v1BleServiceUuid)) {
            tapkeyBleAdvertisingFormatBuilder.addV1Format(v1BleServiceUuid)
        }

        val tapkeyBleAdvertisingFormat = tapkeyBleAdvertisingFormatBuilder
            .build()

        val config = TapkeyEnvironmentConfigBuilder()
        config.setBaseUri(getString(R.string.tapkey_base_uri))

        val b = TapkeyServiceFactoryBuilder(this)
            .setTokenRefreshHandler(TapkeyTokenRefreshHandler(this))
            .setBluetoothAdvertisingFormat(tapkeyBleAdvertisingFormat)
            .setServerClock(serverClock)
            .setConfig(config.build())
            .setPushNotificationTokenProvider(FirebasePushNotificationTokenProvider.Instance)
        val sf = b.build()
        tapkeyServiceFactory = sf

        PollingScheduler.register(this, 1, PollingScheduler.DEFAULT_INTERVAL)

        if(resources.getBoolean(R.bool.use_nfc_features)){
            val hceTagHandler = HceTagHandler(this, sf, serverClock)

            TapkeyHceService.getOnNewTagObservable().addObserver { intent: Intent? ->
                hceTagHandler.handleHceIntent(intent)
            }
        }

        Firebase.analytics.logEvent(AnalyticsEvents.Events.APP_STARTED, null)
    }

    override fun getTapkeyServiceFactory(): TapkeyServiceFactory {
        return tapkeyServiceFactory!!
    }
}