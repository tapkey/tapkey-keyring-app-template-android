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

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.tapkey.util.TosUtil
import io.tapkey.wl.app.R
import io.tapkey.wl.app.databinding.FragmentPhoneNumberInputBinding

class PhoneNumberInputFragment : Fragment() {

    lateinit var binding: FragmentPhoneNumberInputBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPhoneNumberInputBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.signInButton.setOnClickListener {
            signIn()
        }
        binding.checkboxTextView.setOnClickListener { TosUtil.openTos(requireContext()) }
        binding.phoneNumber.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) (context as LoginActivity).hideKeyboard() }
        binding.phoneNumber.setOnEditorActionListener { field , _ , keyEvent ->

            if(keyEvent?.action != KeyEvent.ACTION_DOWN || keyEvent.keyCode != KeyEvent.KEYCODE_ENTER) {
                return@setOnEditorActionListener false
            }

            if(field.text.isNullOrEmpty()){
                return@setOnEditorActionListener true
            }

            (context as LoginActivity).hideKeyboard()
            binding.phoneNumber.clearFocus()

            if(binding.checkboxTos.isChecked) {
                signIn()
            }

            return@setOnEditorActionListener true
        }
    }

    private fun signIn() {
        val phoneInput = binding.phoneNumber.text.toString()
        if (!phoneInput.startsWith("+")) {
            binding.phoneNumber.error = getString(R.string.phone_number_error_missing_code)
            return
        }

        (context as LoginActivity).startPhoneNumberVerification(phoneInput)
    }

    companion object {
        @JvmStatic
        fun newInstance() = PhoneNumberInputFragment()
    }
}