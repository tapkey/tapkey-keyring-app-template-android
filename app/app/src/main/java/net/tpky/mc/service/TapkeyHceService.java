
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

package net.tpky.mc.service;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Intent;
import android.content.res.Resources;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import com.tapkey.mobile.concurrent.Async;
import com.tapkey.mobile.utils.Observable;

import net.tpky.mc.nfc.NdefConnectionUtils;
import net.tpky.mc.nfc.TkTag;
import net.tpky.mc.tlcp.codec.CodecUtils;
import net.tpky.mc.utils.ConnectionUtils;
import net.tpky.mc.utils.Log;
import net.tpky.mc.utils.NfcUtils;
import net.tpky.mc.utils.ObserverSubject;
import net.tpky.nfc.DisconnectableIsoDepConnection;
import net.tpky.nfc.NdefConnection;
import net.tpky.nfc.TkTagTechnology;
import net.tpky.nfc.ce.ISO7816Constants;
import net.tpky.nfc.ce.SelectableTag;
import net.tpky.nfc.ce.TapkeyReverseConnectionTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import io.tapkey.wl.app.R;

@TargetApi(19)
public class TapkeyHceService extends HostApduService {

	private static final String LOG_TAG = TapkeyHceService.class.getSimpleName();

	private static class KeyValuePair<K, V> {
		K key;
		V value;
		private KeyValuePair(K key, V value) {
			super();
			this.key = key;
			this.value = value;
		}
	}

	private static final WeakHashMap<TapkeyHceService, KeyValuePair<String, Map<Integer, Object>>> currentConnections = new WeakHashMap<>();

	static {
		NfcUtils.addTechnologyResolver((tag, technologyType) -> getTechnology(tag, technologyType));
	}

	private static final ObserverSubject<Intent> newTagObserverSubject = new ObserverSubject<>();
	public static Observable<Intent> getOnNewTagObservable() {
		return newTagObserverSubject.getObservable();
	}

	
	private TapkeyReverseConnectionTag tag;
	

	public TapkeyHceService() {
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		TapkeyReverseConnectionTag.ConnectionCallback connectionCallback = new TapkeyReverseConnectionTag.ConnectionCallback() {
			@Override
			public void onNewConnection(DisconnectableIsoDepConnection isoDepConnection) {
				TapkeyHceService.this.onNewConnection(isoDepConnection);
			}
		};

		Application app = this.getApplication();

		try {
			Resources resources = app.getResources();
			String aidHex = resources.getString(R.string.tk_iso7816_aid);
			byte[] aid = CodecUtils.parseFromHex(aidHex);
			this.tag = new TapkeyReverseConnectionTag(aid, connectionCallback);
		} catch (RuntimeException e) {
			Log.e(LOG_TAG, "Couldn't parse AID and create emulated tag.", e);
		}
	}
	
	@Override
	public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
		if (this.tag == null)
			return ISO7816Constants.SW_FUNC_NOT_SUPPORTED;

        try {
            return tag.processCommandApdu(apdu, new SelectableTag.ResponseConsumer() {
                @Override
                public void sendResponseApdu(byte[] response) {
                    TapkeyHceService.this.sendResponseApdu(response);
                }
            });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Couldn't process command APDU.", e);
            try {
                tag.onDeactivated(0);
            } catch (Exception e1) {
                Log.e(LOG_TAG, "Couldn't deactivate pending connection.", e1);
            }
            return new byte[0];
        }
	}

	@Override
	public void onDeactivated(int reason) {
		if (this.tag == null)
			return;
		
		Log.i(LOG_TAG, "Deactivated; reason: " + reason);
		this.tag.onDeactivated(reason);
	}
	
	public DisconnectableIsoDepConnection getIsoDepConnection() {
		return this.tag.getIsoDepConnection();
	}
	
	private void onNewConnection(final DisconnectableIsoDepConnection connection) {
		
		// TODO: prevent from removal!
		
		Async.executeAsync(() -> {
			// must execute this on worker because it reads the tag content, which will block and fail if executed on main thread.
			NdefConnection ndef = NdefConnectionUtils.getAndInitNdefConnectionInBackground(connection);

			// let forceDisconnection() abort the reverse CE connection directly instead of
			// sending a disconnect request on ISO 7816 level. The latter wouldn't be
			// sufficient, because the peer would still answer the disconnect request via
			// a transceive command, which wouldn't complete as long as we wouldn't send any
			// additional data. Aborting the reverse connection will cause the connection to
			// be aborted on ISO 7816 too.
			return ConnectionUtils.makeDisconnectable(ndef, () -> connection.forceDisconnection());
		}).continueOnUi(ndef -> {
			Map<Integer, Object> technologies = new HashMap<>();
			technologies.put(TkTagTechnology.ISO_DEP, connection);
			technologies.put(TkTagTechnology.NDEF, ndef);

			dispatchNewConnection(technologies);
			return null;
		}).catchOnUi(e -> {
			Log.d(LOG_TAG, "Error when handling new connection.", e);
			return null;
		}).conclude();
	}

	private static Object getTechnology(TkTag tag, int technologyType) {
		
		String id = tag.getTagId();
		// any synchronization here? Shouldn't be necessary as only accessed from UI thread.
		// Might OS interfere here when cleaning up?
		for (KeyValuePair<String, Map<Integer, Object>> entry : TapkeyHceService.currentConnections.values()) {
			if (id.equals(entry.key))
				return entry.value.get(technologyType);
		}

		return null;
	}
	
	private void dispatchNewConnection(Map<Integer, Object> technologies) {
		String newId = UUID.randomUUID().toString();
		KeyValuePair<String, Map<Integer, Object>> newConnection = new KeyValuePair<>(newId, technologies);
		TapkeyHceService.currentConnections.put(this, newConnection);
		
		TkTag tag = new TkTag(newId);

		Intent intent = new Intent();
		intent.putExtra(TkTag.EXTRA_TK_TAG, tag);

		newTagObserverSubject.invoke(intent);
	}
}