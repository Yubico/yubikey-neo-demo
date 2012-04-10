/* 	Copyright (c) 2012, Yubico AB.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

* Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided
with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
*/

package com.yubico.demo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.yubico.client.v2.YubicoClient;
import com.yubico.client.v2.YubicoResponse;
import com.yubico.client.v2.exceptions.YubicoValidationException;
import com.yubico.client.v2.exceptions.YubicoValidationFailure;

public class YubiKeyNEOActivity extends Activity {
	private static final String logName = "YubiKeyNEOActivity";
	// compile a pattern that matches a 44 characters modhex at the end of the tag
	private static final Pattern otpPattern = Pattern.compile("^.*([cbdefghijklnrtuv]{44})$");
	private String otp = null;

	private static final int COPY_TO_CLIPBOARD = 0;
	private static final int SHOW_OTP = 1;
	private static final int YUBIKEY_DEMO = 2;
	private static final int YUBICLOUD_VERIFY = 3;
	
	// client id and key from https://upgrade.yubico.com/getapikey/, get your own
	private static final int CLIENT_ID = 7392;
	private static final String CLIENT_KEY = "CQLkl27pz8dhtjAis5ErwTR2KoM=";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    public void onPause() {
        super.onPause();
        // disable foreground dispatch when we're paused
        NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
    }

    public void onResume() {
        super.onResume();
    	PendingIntent pendingIntent = PendingIntent.getActivity(
    			this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    	// register for all NDEF tags starting with http och https
    	IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
    	ndef.addDataScheme("http");
    	ndef.addDataScheme("https");
    	// register for foreground dispatch so we'll receive tags according to our intent filters
    	NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(
    			this, pendingIntent, new IntentFilter[] {ndef}, null);
    }

    public void onNewIntent(Intent intent) {
    	// get the actual URI from the ndef tag
    	String data = intent.getDataString();
        Log.d(logName, "data: " + data);
        Matcher matcher = otpPattern.matcher(data);
        if(matcher.matches()) {
        	// if the otp matched our regex open up a contextmenu
        	otp = matcher.group(1);
        	View view = findViewById(R.id.text1);
        	registerForContextMenu(view);
        	this.openContextMenu(view);
        } else {
        	Toast.makeText(this, R.string.no_neo, Toast.LENGTH_SHORT);
        }
    }
    
    
    
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		menu.setHeaderTitle(R.string.otp_received);
		menu.setHeaderIcon(R.drawable.yubikey);
		menu.add(0, COPY_TO_CLIPBOARD, 0, R.string.copy_to_clipboard);
		menu.add(0, SHOW_OTP, 1, R.string.show_otp);
		menu.add(0, YUBIKEY_DEMO, 2, R.string.yubikey_demo);
		menu.add(0, YUBICLOUD_VERIFY, 3, R.string.yubicloud_verify);
	}
	
	

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case COPY_TO_CLIPBOARD: // copy the OTP to clipboard
			ClipboardManager clipboard = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setText(otp);
			Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
			break;
		case SHOW_OTP: // show a dialog with the OTP displayed
			showOTPDialog(otp);
			break;
		case YUBIKEY_DEMO: // start an intent for the browser with the yubico demo site + the OTP
			String url = "http://demo.yubico.com/php-yubico/one_factor.php?key=" + otp;
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
			break;
		case YUBICLOUD_VERIFY: // do verification of the OTP to the YubiCloud platform directly
			YubicoClient client = YubicoClient.getClient(CLIENT_ID);
			// key is used for signing and verifying request/response, you should use your own.
			client.setKey(CLIENT_KEY);
			try {
				YubicoResponse response = client.verify(otp);
				showCloudDialog(response);
			} catch (YubicoValidationException e) {
				Toast.makeText(this, "Validation failed: " + e.getMessage(), Toast.LENGTH_LONG);
			} catch (YubicoValidationFailure e) {
				Toast.makeText(this, "Failure in validating response: " + e.getMessage(), Toast.LENGTH_LONG);
			}
			break;
		}
		return false;
	}
	
	private void showCloudDialog(YubicoResponse response) {
		// build a dialog from the cloud display view, displaying data from the YubiCloud response
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		View display = View.inflate(this, R.layout.cloud_display, null);

		((TextView) display.findViewById(R.id.status1)).setText(response.getStatus().toString());
		if(response.getOtp() != null) {
			TextView otp_view = (TextView) display.findViewById(R.id.otp1);
			otp_view.setText(formatOTP(otp));
		}
		if(response.getSessioncounter() != null) {
			// display the sessionCounter returned
			((TextView) display.findViewById(R.id.counter1)).setText(response.getSessioncounter());
		}
		if(response.getSl() != null) {
			// display achieved sync percentage 
			((TextView) display.findViewById(R.id.sync1)).setText(response.getSl() + "%");
		}
		alert.setView(display);
		alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog,
					int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}

	private void showOTPDialog(String otp) {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		// Set an EditText view to get user input
		final TextView input = (TextView) TextView.inflate(this,
				R.layout.otp_display, null);

		input.setText(formatOTP(otp));
		alert.setView(input);
		alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog,
					int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}

	private String formatOTP(String otp) {
		String displayOTP = new String();
		for (int i = 0; i < otp.length();) {
			if (i == 12 || i == 26) {
				displayOTP += otp.substring(i, i + 2) + "  ";
				i += 2;
			} else {
				displayOTP += otp.substring(i, i + 4);
				if (i != 40)
					displayOTP += "  ";
				i += 4;
			}
		}
		return displayOTP;
	}
}