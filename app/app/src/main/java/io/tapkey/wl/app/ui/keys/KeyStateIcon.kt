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

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import io.tapkey.wl.app.R


class KeyStateIcon(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {

    enum class LockState {
        Idle,
        Unlocked,
        Failed
    }

    private val STATE_NEARBY = intArrayOf(R.attr.state_nearby)
    private val STATE_UNLOCKED = intArrayOf(R.attr.state_unlocked)
    private val STATE_FAILED = intArrayOf(R.attr.state_failed)

    var nearby: Boolean = false

    var state: LockState = LockState.Idle

    override fun onCreateDrawableState(extraSpace: Int): IntArray? {
        val drawableState = super.onCreateDrawableState(extraSpace + 2)

        if (nearby) {
            mergeDrawableStates(drawableState, STATE_NEARBY)
        }

        if(state == LockState.Unlocked) {
            mergeDrawableStates(drawableState, STATE_UNLOCKED)
        } else if (state == LockState.Failed) {
            mergeDrawableStates(drawableState, STATE_FAILED)
        }

        return drawableState
    }
}