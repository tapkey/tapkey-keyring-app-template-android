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

package io.tapkey.wl.app.ui.login

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import io.tapkey.wl.app.R
import io.tapkey.wl.app.databinding.FragmentCodeVerificationBinding
import java.util.concurrent.TimeUnit
import kotlin.math.ceil


class CodeVerificationFragment : Fragment() {

    lateinit var binding: FragmentCodeVerificationBinding
    private lateinit var countDownTimer: CountDownTimer

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCodeVerificationBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Autofocus and open keyboard for verification code text field
        binding.verificationCode.requestFocus()
        (requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)
            ?.showSoftInput(binding.verificationCode, InputMethodManager.SHOW_IMPLICIT)

        binding.continueSigningIn.setOnClickListener {
            (context as LoginActivity).verifyPhoneNumberWithCode(binding.verificationCode.text.toString())
        }

        binding.resendCode.setOnClickListener {
            (context as LoginActivity).resendVerificationCode()
            binding.resendCode.isEnabled = false
            countDownTimer.cancel()
            countDownTimer.start()
        }

        countDownTimer = object : CountDownTimer(60000, 250) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsUntilFinished: Long =
                    ceil(millisUntilFinished.toDouble() / 1000).toLong()
                binding.resendCode.text = getString(R.string.resend_code_in).format(
                    TimeUnit.SECONDS.toSeconds(secondsUntilFinished)
                )
            }

            override fun onFinish() {
                binding.resendCode.isEnabled = true
                binding.resendCode.text = getString(R.string.resend_code)
            }
        }
        countDownTimer.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer.cancel()
    }

    companion object {
        @JvmStatic
        fun newInstance() = CodeVerificationFragment()
    }
}