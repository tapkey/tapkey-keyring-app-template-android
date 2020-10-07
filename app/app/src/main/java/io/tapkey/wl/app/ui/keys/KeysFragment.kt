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

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tapkey.mobile.ble.BleLockScanner
import com.tapkey.mobile.manager.NotificationManager
import com.tapkey.mobile.manager.pollForNotifications
import com.tapkey.mobile.manager.queryLocalKeys
import com.tapkey.mobile.model.KeyDetails
import com.tapkey.mobile.utils.ObserverRegistration
import io.sentry.Sentry
import io.tapkey.wl.app.App
import io.tapkey.wl.app.R
import io.tapkey.wl.app.databinding.FragmentKeysBinding
import kotlinx.coroutines.launch
import net.tpky.mc.utils.ObserverSubject


class KeysFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private val TAG = KeysFragment::class.java.simpleName

    private var _binding: FragmentKeysBinding? = null
    private val binding get() = _binding!!

    private lateinit var keysListAdapter: KeysListAdapter
    private var isReceiverRegistered = false
    private var adapterStateChangeReceiver: AdapterStateChangeReceiver = AdapterStateChangeReceiver()
    private lateinit var bleScanner: BleLockScanner
    private var bleScanObserverRegistration: ObserverRegistration? = null
    private var nearbyLocksObserverRegistration: ObserverRegistration? = null
    private var keyUpdateObserverRegistration: ObserverRegistration? = null
    private lateinit var nfcManager: NfcManager
    private var bleManager: BluetoothAdapter? = null
    private lateinit var notificationManager: NotificationManager

    private var allPermissionsGrantedObserver: ObserverSubject<Boolean> = ObserverSubject()

    private var pinnedKeys: MutableList<String> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = (requireActivity().application as App)
        nfcManager = requireContext().getSystemService(Context.NFC_SERVICE) as NfcManager
        bleManager = BluetoothAdapter.getDefaultAdapter()
        notificationManager = app.tapkeyServiceFactory.notificationManager

        keysListAdapter = KeysListAdapter(requireContext(), lifecycleScope, app.tapkeyServiceFactory.bleLockCommunicator, app.tapkeyServiceFactory.commandExecutionFacade, ArrayList())
        binding.keysList.setHasFixedSize(true)
        binding.keysList.adapter = keysListAdapter
        binding.keysList.layoutManager = LinearLayoutManager(context)

        bleScanner = app.tapkeyServiceFactory.bleLockScanner

        binding.swipeContainer.setOnRefreshListener(this)

        binding.enableNfcButton.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
        }

        binding.enableBleButton.setOnClickListener {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1)
        }

        binding.enableLocationButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.enableBlePermissionButton.setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 3)
            }
        }

        lifecycleScope.launch {
            binding.keysList.visibility = View.GONE
            binding.loadingCircle.visibility = View.VISIBLE

            refreshKeys()

            binding.loadingCircle.visibility = View.GONE
            binding.keysList.visibility = View.VISIBLE
        }

    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)

        if (resources.getBoolean(R.bool.use_nfc_features)) {
            if (!isReceiverRegistered) {
                intentFilter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            }
            setNfcWarningShown(!(nfcManager.defaultAdapter != null && nfcManager.defaultAdapter.isEnabled))
        }

        requireActivity().registerReceiver(adapterStateChangeReceiver, intentFilter)
        isReceiverRegistered = true

        val allPermissionGranted: Boolean

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {

            val locationPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            setLocationWarningShown(!locationPermissionGranted)
            allPermissionGranted = locationPermissionGranted

        } else {

            val bleScanPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            val bleConnectPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            setBlePermissionWarningShown(!(bleScanPermissionGranted && bleConnectPermissionGranted))
            allPermissionGranted = bleScanPermissionGranted && bleConnectPermissionGranted

        }

        setBleDisabledWarningShown(allPermissionGranted && bleManager?.isEnabled != true)

        allPermissionsGrantedObserver.observable.addObserver {
            @SuppressLint("MissingPermission")
            if (it) bleScanObserverRegistration = bleScanner.startForegroundScan()
        }

        updatePermissionStatus()

        nearbyLocksObserverRegistration = bleScanner.locksChangedObservable
            .addObserver {
                lifecycleScope.launch {
                    refreshKeys()
                }
            }

        keyUpdateObserverRegistration = (requireActivity().application as App).tapkeyServiceFactory.keyManager.keyUpdateObservable
            .addObserver {
                lifecycleScope.launch {
                    refreshKeys()
                }
            }
    }

    override fun onPause() {
        super.onPause()
        if (isReceiverRegistered) {
            try {
                requireActivity().unregisterReceiver(adapterStateChangeReceiver)
            } catch (e: IllegalArgumentException) {
                // Do nothing
            }
            isReceiverRegistered = false
        }

        bleScanObserverRegistration?.close()
        bleScanObserverRegistration = null
        keyUpdateObserverRegistration?.close()
        keyUpdateObserverRegistration = null
        nearbyLocksObserverRegistration?.close()
        nearbyLocksObserverRegistration = null
    }

    inner class AdapterStateChangeReceiver : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            if (intent?.action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                val state = intent?.getIntExtra(
                    NfcAdapter.EXTRA_ADAPTER_STATE,
                    NfcAdapter.STATE_OFF
                )
                setNfcWarningShown(!(state == NfcAdapter.STATE_ON || state == NfcAdapter.STATE_TURNING_ON))
                updatePermissionStatus()
            } else if (intent?.action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                val state = intent?.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.STATE_OFF
                )
                updatePermissionStatus()
            }
        }
    }

    private fun updatePermissionStatus() {
        val bleEnabled = bleManager?.isEnabled == true

        var allPermissionGranted = true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {

            val locationPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            setLocationWarningShown(!locationPermissionGranted)
            allPermissionGranted = allPermissionGranted && locationPermissionGranted

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val bleScanPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            val bleConnectPermissionGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            setBlePermissionWarningShown(!(bleScanPermissionGranted && bleConnectPermissionGranted))
            allPermissionGranted = allPermissionGranted && bleScanPermissionGranted && bleConnectPermissionGranted
        }

        setBleDisabledWarningShown(allPermissionGranted && !bleEnabled)

        this.allPermissionsGrantedObserver.invoke(bleEnabled && allPermissionGranted)
    }

    private fun setNfcWarningShown(isShown: Boolean) {
        binding.nfcDisabledContainer.visibility = if (resources.getBoolean(R.bool.use_nfc_features) && isShown) View.VISIBLE else View.GONE
    }

    private fun setBleDisabledWarningShown(isShown: Boolean) {
        binding.bleDisabledContainer.visibility = if (isShown) View.VISIBLE else View.GONE
    }

    private fun setBlePermissionWarningShown(isShown: Boolean) {
        binding.blePermissionContainer.visibility = if (isShown) View.VISIBLE else View.GONE
    }

    private fun setLocationWarningShown(isShown: Boolean) {
        binding.accessLocationContainer.visibility = if (isShown) View.VISIBLE else View.GONE
    }

    private suspend fun refreshKeys() {
        val sf = (activity?.application as App).tapkeyServiceFactory
        val users = sf.userManager.users

        val list: List<KeyDetails> = try {
            if (users.size > 0) sf.keyManager.queryLocalKeys(users[0]) else ArrayList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query local keys: ", e)
            Sentry.captureException(e)
            ArrayList()
        }

        val keyDetailsArrayList = ArrayList<NearbyKey>()
        for (key in list) {
            val isNearby = bleScanner.isLockNearby(key.grant.boundLock.physicalLockId)
            var bluetoothAddress: String? = null

            if (isNearby) {
                bluetoothAddress = bleScanner.getLock(key.grant.boundLock.physicalLockId)?.bluetoothAddress
            }

            keyDetailsArrayList.add(NearbyKey(key, isNearby ||pinnedKeys.contains(key.grant.boundLock.physicalLockId), bluetoothAddress, key.grant.boundLock.physicalLockId) {
                pinnedKeys.add(key.grant.boundLock.physicalLockId)
                return@NearbyKey {
                    pinnedKeys.remove(key.grant.boundLock.physicalLockId)
                    lifecycleScope.launch {
                        refreshKeys()
                    }
                }
            })
        }

        keyDetailsArrayList
            .sortBy { it.keyDetails.grant?.boundLock?.title }
        keyDetailsArrayList
            .sortByDescending { it.isNearby }

        keysListAdapter.clear()
        keysListAdapter.addAll(keyDetailsArrayList)
        keysListAdapter.notifyDataSetChanged()
    }

    override fun onRefresh() {
        lifecycleScope.launch {
            val sf = (activity?.application as App).tapkeyServiceFactory
            sf.notificationManager.pollForNotifications()
            refreshKeys()
            binding.swipeContainer.isRefreshing = false
        }
    }
}