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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.tapkey.mobile.ble.BleLockCommunicator
import com.tapkey.mobile.ble.executeCommand
import com.tapkey.mobile.manager.CommandExecutionFacade
import com.tapkey.mobile.manager.triggerLock
import com.tapkey.mobile.model.CommandResult
import com.tapkey.mobile.model.KeyDetails
import com.tapkey.mobile.model.UserGrant
import io.tapkey.util.MessageResolver
import io.tapkey.wl.app.R
import io.tapkey.wl.app.util.AnalyticsEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Adapter for the KeyRing RecyclerView
 */
class KeysListAdapter(private val context: Context, private val scope: CoroutineScope, private val bleLockCommunicator: BleLockCommunicator, private val commandExecutionFacade: CommandExecutionFacade, private val dataSource: ArrayList<NearbyKey>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private enum class ViewType(val value: Int) {
        // Lock is nearby and ready for unlocking
        Nearby(1),
        // Lock is not nearby but key is valid
        Key(2),
        // Nearby or Other heading
        Heading(3),
        // Text for no keys nearby
        NoNearby(4);

        companion object {
            private val VALUES = values()
            // Convert Int to enum
            fun fromInt(value: Int) = VALUES.firstOrNull { it.value == value }
        }
    }

    private val messageResolver = MessageResolver(context)

    // Total number of headings in list (including no keys nearby text)
    private var NUMBER_OF_HEADERS = calculateNumberOfHeaders()
    // Position of the first header ("Nearby") - always first
    private val FIRST_HEADER_POS = 0
    // Position of the second header ("Other") - may change
    private var SECOND_HEADER_POS = calculateSecondHeaderPosition()

    // ViewHolder for the no locks nearby text
    inner class NoLocksNearbyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    // ViewHolder for the nearby and other headings
    inner class HeadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var listHeadingTextView: TextView? = null

        init {
            listHeadingTextView = itemView.findViewById<View>(R.id.list_heading) as TextView
        }

        fun bind(position: Int) {
            // If the position is the position of the first header, use text for "Nearby"
            // Else use the "Other" text
            listHeadingTextView?.text = if (position == FIRST_HEADER_POS) {
                context.getString(R.string.nearby)
            } else {
                context.getString(R.string.other)
            }
        }
    }

    // ViewHolder for the nearby and non-nearby keys
    inner class KeyViewHolder(itemView: View) : RecyclerView.ViewHolder(
        itemView
    ) {
        private var titleTextView: TextView? = null
        private var subtitleTextView: TextView? = null
        private var errorTextView: TextView? = null
        private var openButton: Button? = null
        private var loadingCircle: ProgressBar? = null
        private var statusImage: ImageView? = null
        private var icon: KeyStateIcon? = null

        init {
            titleTextView = itemView.findViewById<View>(R.id.first_line) as TextView
            subtitleTextView = itemView.findViewById<View>(R.id.second_line) as TextView
            errorTextView = itemView.findViewById<View>(R.id.third_line) as? TextView
            openButton = itemView.findViewById<View>(R.id.openButton) as? Button
            loadingCircle = itemView.findViewById<View>(R.id.openLoading) as? ProgressBar
            statusImage = itemView.findViewById<View>(R.id.statusIcon) as? ImageView
            icon = itemView.findViewById<View>(R.id.icon) as? KeyStateIcon
        }

        fun bind(key: NearbyKey) {
            titleTextView?.text = key.keyDetails.grant.boundLock.title
            subtitleTextView?.text =
                if (hasKeyUnlimitedValidity(key.keyDetails.grant)) {
                    context.getString(R.string.unrestricted)
                } else {
                    context.getString(R.string.restricted)
                }
            this.itemView.setOnClickListener {
                onClick(key.keyDetails)
            }
            if (key.isNearby) {
                icon?.nearby = true
                icon?.refreshDrawableState()
                openButton?.setOnClickListener {
                    openButton!!.visibility = View.INVISIBLE
                    loadingCircle!!.visibility = View.VISIBLE

                    scope.launch {
                        val unpinFn = key.pinFn()
                        try{
                            triggerLock(key, loadingCircle!!, statusImage!!, openButton!!, errorTextView!!, icon!!)
                        } finally {
                            unpinFn()
                        }
                    }
                }
            } else {
                icon?.nearby = false
                icon?.refreshDrawableState()
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context

        if (ViewType.fromInt(viewType) == ViewType.Heading) {
            // Inflate the heading row
            return HeadingViewHolder(LayoutInflater.from(context).inflate(R.layout.key_list_heading, parent, false))
        }

        if (ViewType.fromInt(viewType) == ViewType.NoNearby) {
            // Inflate the no nearby keys row
            return NoLocksNearbyViewHolder(LayoutInflater.from(context).inflate(R.layout.key_list_no_items, parent, false))
        }

        var resId = 0

        if (ViewType.fromInt(viewType) == ViewType.Nearby) {
            // Use the nearby keys layout
            resId = R.layout.key_list_item_nearby
        }

        if (ViewType.fromInt(viewType) == ViewType.Key) {
            // Use the keys layout
            resId = R.layout.key_list_item
        }

        val keyViewHolder = LayoutInflater.from(context).inflate(resId, parent, false)
        return KeyViewHolder(keyViewHolder)
    }

    override fun getItemViewType(position: Int): Int {
        // Use the heading view type if we're about to show the first position
        // or if we're in the position of the second header.
        if (position == FIRST_HEADER_POS || position == SECOND_HEADER_POS) {
            return ViewType.Heading.value
        }

        // If we're in the position after the first heading and there are no locks nearby
        // we show the no nearby locks text.
        if (position == FIRST_HEADER_POS + 1 && dataSource.none { it.isNearby }) {
            return ViewType.NoNearby.value
        }

        // If the lock in the array of the offset position is nearby use the nearby view
        // otherwise use the "no-nearby" view
        return if (dataSource[getOffsetPosition(position)].isNearby) {
            ViewType.Nearby.value
        } else {
            ViewType.Key.value
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        // When the ViewHolder is a heading we bind the heading view
        if (viewHolder is HeadingViewHolder) {
            viewHolder.bind(position)
        } else if (viewHolder is KeyViewHolder) {
            // Otherwise we bind the key view and use the correct value of the data source
            viewHolder.bind(dataSource[getOffsetPosition(position)])
        }
    }

    /**
     * Method to convert a recycler view position to its corresponding array position
     */
    private fun getOffsetPosition(originalPosition: Int): Int {
        // First we check if any locks are nearby
        // If no locks are nearby we can immediately calculate the position, since
        // before the first position there are exactly three rows:
        // Heading 1 - No Nearby Locks Text - Heading 2
        return if (dataSource.any { it.isNearby }) {
            // If we're before the second header (i.e. populating the nearby locks), there's
            // only one heading to offset.
            // If there are only nearby locks there's only one heading too and we'll never show
            // the second heading.
            // Otherwise we've already shown two headings, so we need to offset by 2
            if (originalPosition < SECOND_HEADER_POS || dataSource.all { it.isNearby }) {
                originalPosition - 1
            } else {
                originalPosition - 2
            }
        } else {
            originalPosition - 3
        }
    }

    /**
     * This methods calculates the correct position of the second header ("other")
     */
    private fun calculateSecondHeaderPosition(): Int {
        // If there are no locks nearby the position is the second
        // 0 - Nearby heading
        // 1 - No locks nearby text
        // 2 - Other heading
        if (dataSource.none { it.isNearby }) {
            return 2
        }

        // If all locks are nearby the second header must not be shown
        if (dataSource.all { it.isNearby }) {
            return -1
        }

        // Otherwise we offset by 1 for the nearby heading and by the amount of nearby locks shown
        return 1 + dataSource.count { it.isNearby }
    }

    /**
     * Method to calculate the number of headers
     * Note: The no nearby locks text is counted as a heading
     */
    private fun calculateNumberOfHeaders(): Int {
        // No locks are nearby, show Nearby, no nearby locks text and Other heading
        if (dataSource.none { it.isNearby }) {
            return 3
        }
        // All locks are nearby, only show nearby heading
        if (dataSource.all { it.isNearby }) {
            return 1
        }
        // Show Nearby and Other heading
        return 2
    }

    override fun getItemCount(): Int {
        return dataSource.size + NUMBER_OF_HEADERS
    }

    fun clear() {
        dataSource.clear()
        refreshHeadings()
    }

    fun addAll(list: List<NearbyKey>) {
        dataSource.addAll(list)
        refreshHeadings()
    }

    private fun refreshHeadings() {
        this.NUMBER_OF_HEADERS = calculateNumberOfHeaders()
        this.SECOND_HEADER_POS = calculateSecondHeaderPosition()
    }

    private fun onClick(keyDetails: KeyDetails) {
        val intent = Intent(context, KeyDetailsActivity::class.java).apply {
            putExtra(EXTRA_LOCK_NAME, keyDetails.grant.boundLock.title)
            putExtra(EXTRA_UNLIMITED_VALIDITY, hasKeyUnlimitedValidity(keyDetails.grant))
            putExtra(EXTRA_OFFLINE_FROM, keyDetails.autorenewedBefore)
            putExtra(EXTRA_OFFLINE_UNTIL, keyDetails.autoRenewalScheduledAt)
        }
        context.startActivity(intent)
    }

    private fun hasKeyUnlimitedValidity(grant: UserGrant): Boolean {
        return grant.validBefore == null && grant.validFrom == null && grant.timeRestrictionIcal == null
    }

    @SuppressLint("MissingPermission")
    private suspend fun triggerLock(key: NearbyKey, loadingCircle: ProgressBar, statusImage: ImageView, openButton: Button, errorTextView: TextView, keyStateIcon: KeyStateIcon) {

        val analyticsBundle = Bundle()
        analyticsBundle.putString(AnalyticsEvents.Parameters.TECHNOLOGY, "ble")
        Firebase.analytics.logEvent(AnalyticsEvents.Events.TRIGGER_LOCK_STARTED, analyticsBundle)


        try {
            val commandResult = withTimeout(15000) {
                bleLockCommunicator.executeCommand(key.bluetoothAddress, key.physicalLockId) {
                    commandExecutionFacade.triggerLock(it)
                }
            }

            loadingCircle.visibility = View.GONE

            if (commandResult.commandResultCode == CommandResult.CommandResultCode.Ok) {

                Firebase.analytics.logEvent(AnalyticsEvents.Events.TRIGGER_LOCK_SUCCEEDED, analyticsBundle)

                statusImage.setImageResource(R.drawable.ic_open_success)
                keyStateIcon.state = KeyStateIcon.LockState.Unlocked
                keyStateIcon.refreshDrawableState()
            } else {

                analyticsBundle.putString(AnalyticsEvents.Parameters.COMMAND_RESULT_CODE, commandResult.commandResultCode.name)
                Firebase.analytics.logEvent(AnalyticsEvents.Events.TRIGGER_LOCK_FAILED, analyticsBundle)

                errorTextView.text = messageResolver.getMessage(commandResult.commandResultCode)
                keyStateIcon.state = KeyStateIcon.LockState.Failed
                keyStateIcon.refreshDrawableState()

                if (messageResolver.getSeverity(commandResult.commandResultCode) == MessageResolver.Severity.Warning) {
                    statusImage.setImageResource(R.drawable.ic_open_warning)
                } else if (messageResolver.getSeverity(commandResult.commandResultCode) == MessageResolver.Severity.Error) {
                    statusImage.setImageResource(R.drawable.ic_open_error)
                }

                errorTextView.visibility = View.VISIBLE
            }

            statusImage.visibility = View.VISIBLE
        } catch (e: Exception) {

            Firebase.analytics.logEvent(AnalyticsEvents.Events.TRIGGER_LOCK_FAILED, analyticsBundle)

            keyStateIcon.state = KeyStateIcon.LockState.Failed
            keyStateIcon.refreshDrawableState()
            loadingCircle.visibility = View.GONE
            errorTextView.text = messageResolver.getMessage(e)
            statusImage.setImageResource(R.drawable.ic_open_error)
            errorTextView.visibility = View.VISIBLE
            statusImage.visibility = View.VISIBLE
        }

        delay(4000)

        keyStateIcon.state = KeyStateIcon.LockState.Idle
        keyStateIcon.refreshDrawableState()
        statusImage.visibility = View.GONE
        errorTextView.visibility = View.GONE
        openButton.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_LOCK_NAME = "io.tapkey.wl.app.keys.LOCK_NAME"
        const val EXTRA_UNLIMITED_VALIDITY = "io.tapkey.wl.app.keys.UNLIMITED_VALIDITY"
        const val EXTRA_OFFLINE_FROM = "io.tapkey.wl.app.keys.OFFLINE_FROM"
        const val EXTRA_OFFLINE_UNTIL = "io.tapkey.wl.app.keys.OFFLINE_UNTIL"
    }
}