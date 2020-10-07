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
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.*
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.tapkey.mobile.manager.logIn
import com.tapkey.mobile.manager.pollForNotifications
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.tapkey.wl.app.App
import io.tapkey.wl.app.MainActivity
import io.tapkey.wl.app.R
import io.tapkey.wl.app.databinding.LoginActivityBinding
import io.tapkey.wl.app.services.TapkeyTokenExchangeService
import io.tapkey.wl.app.util.AnalyticsEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit


class LoginActivity : AppCompatActivity() {

    private val TAG = LoginActivity::class.qualifiedName

    private lateinit var binding: LoginActivityBinding
    private lateinit var phoneNumberInputFragment: PhoneNumberInputFragment

    private var codeVerificationFragment: CodeVerificationFragment? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    private var storedVerificationId: String? = ""
    private lateinit var resendToken: ForceResendingToken
    private lateinit var phoneNumber: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        auth = Firebase.auth
        auth.useAppLanguage()

        if (savedInstanceState == null) {
            phoneNumberInputFragment = PhoneNumberInputFragment.newInstance()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, phoneNumberInputFragment)
                .commitNow()
        }

        binding.container.setOnFocusChangeListener { _, hasFocus -> if(hasFocus) hideKeyboard() }

        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "Verification successful, code entry not needed")
                lifecycleScope.launch {
                    signInWithPhoneAuthCredential(credential)
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {

                if (e is FirebaseTooManyRequestsException) {
                    Log.e(TAG, "Verification failed. Too many requests")
                    Sentry.captureMessage("Verification failed. Too many requests")
                    phoneNumberInputFragment.binding.phoneNumber.error = getString(R.string.phone_number_too_many_request)
                    return
                }

                if (e is FirebaseAuthInvalidCredentialsException) {
                    if(e.errorCode == "ERROR_INVALID_PHONE_NUMBER") {
                        Log.e(TAG, "Verification failed. Invalid phone number", e)
                        phoneNumberInputFragment.binding.phoneNumber.error = getString(R.string.phone_number_invalid_error)
                        return
                    }
                }

                Log.e(TAG, "Verification failed", e)
                Sentry.captureException(e)
                phoneNumberInputFragment.binding.phoneNumber.error = getString(R.string.phone_number_error)
            }

            override fun onCodeSent(
                verificationId: String,
                token: ForceResendingToken
            ) {
                storedVerificationId = verificationId
                resendToken = token

                val codeVerificationFragment = supportFragmentManager.findFragmentByTag("CODE_VERIFICATION")

                if (codeVerificationFragment == null || (!codeVerificationFragment.isVisible)) {
                    redirectToCodeEntryFragment()
                }
            }
        }
    }

    fun startPhoneNumberVerification(phoneNumber: String) {
        this.phoneNumber = phoneNumber
        PhoneAuthProvider.getInstance()
            .verifyPhoneNumber(phoneNumber, 60, TimeUnit.SECONDS, this, callbacks)
    }

    fun resendVerificationCode() {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setForceResendingToken(resendToken)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyPhoneNumberWithCode(code: String) {
        isLoadingShown(true)
        val credential = PhoneAuthProvider.getCredential(storedVerificationId.toString(), code)
        lifecycleScope.launch {
            signInWithPhoneAuthCredential(credential)
        }
    }

    private suspend fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        val sf = (application as App).tapkeyServiceFactory

        val user: FirebaseUser?

        try {

            val result = auth.signInWithCredential(credential).await()
            user = result?.user

        } catch (e: FirebaseAuthInvalidCredentialsException) {

            val analyticsBundle = Bundle()
            analyticsBundle.putString(AnalyticsEvents.Parameters.REASON, "invalid_code")
            Firebase.analytics.logEvent(AnalyticsEvents.Events.LOGIN_FAILED, analyticsBundle)

            Log.e(TAG, "Invalid Code", e )
            codeVerificationFragment?.binding?.verificationCode?.error = getString(R.string.wrong_code)
            isLoadingShown(false)
            return
        }
        catch (e: Exception) {
            Log.e(TAG, "Couldn't sign in with Firebase.", e)
            val analyticsBundle = Bundle()
            analyticsBundle.putString(AnalyticsEvents.Parameters.REASON, e.message)
            Firebase.analytics.logEvent(AnalyticsEvents.Events.LOGIN_FAILED, analyticsBundle)
            codeVerificationFragment?.binding?.verificationCode?.error = getString(R.string.code_error)
            isLoadingShown(false)
            Sentry.captureException(e)
            return
        }

        if (user == null) {
            Log.e(TAG, "Failed to sign in with firebase. User is null" )
            Sentry.captureMessage("Failed to sign in with firebase. User is null")
            isLoadingShown(false)
            return
        }

        val firebaseIdToken: String?

        try{

            val tokenResult = user.getIdToken(false).await()
            firebaseIdToken = tokenResult.token

        } catch (e: Exception) {
            Log.e(TAG, "Failed to query id token.")
            Sentry.captureException(e)
            codeVerificationFragment?.binding?.verificationCode?.error = getString(R.string.code_error)
            isLoadingShown(false)
            return
        }


        if (firebaseIdToken == null) {
            Log.e(TAG, "Can not exchange token. IdToken is null")
            Sentry.captureMessage("Can not exchange token. IdToken is null", SentryLevel.ERROR)
            codeVerificationFragment?.binding?.verificationCode?.error = getString(R.string.code_error)
            isLoadingShown(false)
            return
        }

        val accessToken: String?

        try {

            val tokenResponse = TapkeyTokenExchangeService(this@LoginActivity).exchangeToken(firebaseIdToken)
            accessToken = tokenResponse.accessToken

        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            Sentry.captureException(e)
            codeVerificationFragment?.binding?.verificationCode?.error = getString(R.string.code_error)
            isLoadingShown(false)
            return
        }

        if(accessToken == null) {
            Log.e(TAG, "Token exchange failed. Access token is null")
            Sentry.captureMessage("Token exchange failed. Access token is null", SentryLevel.ERROR)
            codeVerificationFragment?.binding?.verificationCode?.error = getString(R.string.code_error)
            isLoadingShown(false)
            return
        }


        try {
            sf.userManager.logIn(accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to login with exchanged access token", e)
            Sentry.captureException(e)
            codeVerificationFragment?.binding?.verificationCode?.error = getString(R.string.code_error)
            isLoadingShown(false)
            return
        }

        GlobalScope.launch(Dispatchers.Main) {
            try {
                sf.notificationManager.pollForNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to poll for notifications", e)
                Sentry.captureException(e)
            }
        }

        Firebase.analytics.logEvent(AnalyticsEvents.Events.LOGIN_SUCCEEDED, null)

        redirectToMainActivity()
    }

    private fun redirectToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun redirectToCodeEntryFragment() {
        val fragment = CodeVerificationFragment.newInstance()
        codeVerificationFragment = fragment
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment, "CODE_VERIFICATION")
            .addToBackStack(CodeVerificationFragment::class.java.name)
            .commit()
    }

    fun goBack(view: View) {
        supportFragmentManager.popBackStackImmediate()
    }

    private fun isLoadingShown(shown: Boolean) {
        if (shown) {
            hideKeyboard()
        }

        codeVerificationFragment?.binding?.verificationCode?.visibility = if (shown) View.GONE else View.VISIBLE
        codeVerificationFragment?.binding?.verificationCode?.requestFocus()
        codeVerificationFragment?.binding?.continueSigningIn?.visibility = if (shown) View.GONE else View.VISIBLE
        codeVerificationFragment?.binding?.resendCode?.visibility = if (shown) View.GONE else View.VISIBLE
        codeVerificationFragment?.binding?.codeVerificationProgressBar?.visibility = if (shown) View.VISIBLE else View.GONE
    }

    fun hideKeyboard() {
        try {
            // Hide keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm!!.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        } catch (e: Exception) {
            // Keyboard cannot be hidden
        }
    }
}