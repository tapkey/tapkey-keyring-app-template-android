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

package io.tapkey.wl.app.ui.keys

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.tapkey.wl.app.R
import io.tapkey.wl.app.databinding.ActivityKeyDetailsBinding
import io.tapkey.wl.app.ui.keys.KeysListAdapter.Companion.EXTRA_LOCK_NAME
import io.tapkey.wl.app.ui.keys.KeysListAdapter.Companion.EXTRA_OFFLINE_FROM
import io.tapkey.wl.app.ui.keys.KeysListAdapter.Companion.EXTRA_OFFLINE_UNTIL
import io.tapkey.wl.app.ui.keys.KeysListAdapter.Companion.EXTRA_UNLIMITED_VALIDITY
import java.util.*

class KeyDetailsActivity : AppCompatActivity() {

    lateinit var binding: ActivityKeyDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyDetailsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.keyDetailsToolbar)
        supportActionBar!!.title = getString(R.string.key_details)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        binding.lockName.text = intent.getStringExtra(EXTRA_LOCK_NAME)
        binding.validity.text = if (intent.getBooleanExtra(EXTRA_UNLIMITED_VALIDITY, false)) {
            getString(R.string.key_always_valid)
        } else {
            getString(R.string.key_restricted)
        }

        val offlineFrom = intent.getSerializableExtra(EXTRA_OFFLINE_FROM)
        val offlineUntil = intent.getSerializableExtra(EXTRA_OFFLINE_UNTIL)

        if (offlineFrom != null && offlineUntil != null) {
            binding.offlineAccess.text = String.format(
                getString(R.string.key_offline_access), intent.getStringExtra(
                    EXTRA_LOCK_NAME
                ),
                offlineFrom as Date,
                offlineUntil as Date
            )
        } else {
            binding.offlineAccess.visibility = View.GONE
            binding.offlineImage.visibility = View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return false
    }
}