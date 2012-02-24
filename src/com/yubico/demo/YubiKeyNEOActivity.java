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

public class YubiKeyNEOActivity extends Activity {
	private static final String logName = "YubiKeyNEOActivity";
	private static final Pattern otpPattern = Pattern.compile("^.*([cbdefghijklnrtuv]{44})$");
	private String otp = null;

	private static final int COPY_TO_CLIPBOARD = 0;
	private static final int SHOW_OTP = 1;
	private static final int YUBIKEY_DEMO = 2;
	private static final int YUBICLOUD_VERIFY = 3;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.main);
    }
    
    public void onPause() {
        NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
    }

    public void onResume() {
    	PendingIntent pendingIntent = PendingIntent.getActivity(
    			this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    	IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
    	ndef.addDataScheme("http");
    	IntentFilter ndefHttps = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
    	ndef.addDataScheme("https");
    	IntentFilter[] intentFiltersArray = new IntentFilter[] {ndef, ndefHttps};
    	NfcAdapter.getDefaultAdapter(this).enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null);
    }

    public void onNewIntent(Intent intent) {
    	String data = intent.getDataString();
        Log.e(logName, "data: " + data);
        Matcher matcher = otpPattern.matcher(data);
        if(matcher.matches()) {
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
		case COPY_TO_CLIPBOARD:
			ClipboardManager clipboard = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setText(otp);
			Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
			break;
		case SHOW_OTP:
			showOTPDialog(otp);
			break;
		case YUBIKEY_DEMO:
			String url = "http://demo.yubico.com/php-yubico/one_factor.php?key=" + otp;
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
			break;
		case YUBICLOUD_VERIFY:
			YubicoClient client = YubicoClient.getClient(7364);
			YubicoResponse response = client.verify(otp);
			showCloudDialog(response);
			break;
		}
		return false;
	}
	
	private void showCloudDialog(YubicoResponse response) {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		// Set an EditText view to get user input
		View input = View.inflate(this, R.layout.cloud_display, null);

		((TextView) input.findViewById(R.id.status1)).setText(response.getStatus().toString());
		if(response.getOtp() != null) {
			TextView otp_view = (TextView) input.findViewById(R.id.otp1);
			if(response.getOtp().equals(otp)) {
				otp_view.setText(formatOTP(otp));
			} else {
				otp_view.setText(R.string.otp_missmatch);
			}
		}
		if(response.getSessioncounter() != null) {
			((TextView) input.findViewById(R.id.counter1)).setText(response.getSessioncounter());
		}
		if(response.getSl() != null) {
			((TextView) input.findViewById(R.id.sync1)).setText(response.getSl());
		}
		alert.setView(input);
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