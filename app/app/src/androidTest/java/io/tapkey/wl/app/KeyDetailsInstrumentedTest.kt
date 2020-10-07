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

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.tapkey.wl.app.ui.keys.KeyDetailsActivity
import io.tapkey.wl.app.ui.keys.KeysListAdapter
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*


@RunWith(AndroidJUnit4::class)
class KeyDetailsInstrumentedTest {

    private var dateFrom = Date(1570665600000) //2019-10-10 00:00:00
    private var dateUntil = Date(1602288000000) //2020-10-10 00:00:00

    private lateinit var scenario: ActivityScenario<KeyDetailsActivity>
    private val intent = Intent(ApplicationProvider.getApplicationContext(), KeyDetailsActivity::class.java).apply {
        putExtra(KeysListAdapter.EXTRA_LOCK_NAME, "Lock Name Test")
        putExtra(KeysListAdapter.EXTRA_UNLIMITED_VALIDITY, false)
        putExtra(KeysListAdapter.EXTRA_OFFLINE_FROM, dateFrom)
        putExtra(KeysListAdapter.EXTRA_OFFLINE_UNTIL, dateUntil)
    }

    @After
    fun cleanup() {
        scenario.close()
    }

    @Test
    fun showsLockName() {
        scenario = launchActivity(intent)
        scenario.onActivity {activity ->
            Assert.assertEquals("Lock Name Test", activity.binding.lockName.text)
        }
    }

    @Test
    fun showsValidity() {
        scenario = launchActivity(intent)
        scenario.onActivity {activity ->
            Assert.assertEquals(activity.applicationContext.getString(R.string.key_restricted), activity.binding.validity.text)
        }
        scenario.close()
        intent.putExtra(KeysListAdapter.EXTRA_UNLIMITED_VALIDITY, true)
        scenario = launchActivity(intent)
        scenario.onActivity {activity ->
            Assert.assertEquals(activity.applicationContext.getString(R.string.key_always_valid), activity.binding.validity.text)
        }
    }

    @Test
    fun showsOfflineAccess() {
        scenario = launchActivity(intent)
        scenario.onActivity {activity ->
            Assert.assertEquals(
                "You can use this key to access Lock Name Test offline until $dateFrom. It will be renewed on $dateUntil.",
                activity.binding.offlineAccess.text
            )
        }
    }

}