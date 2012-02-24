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

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    public void onPause() {
        super.onPause();
        NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);
    }

    public void onResume() {
    	super.onResume();
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
		menu.add(0, 0, 0, R.string.copy_to_clipboard);
		menu.add(0, 1, 1, R.string.show_otp);
		menu.add(0, 2, 2, R.string.yubikey_demo);
		menu.add(0, 3, 3, R.string.yubicloud_verify);
		super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case 0:
			ClipboardManager clipboard = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setText(otp);
			Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
			break;
		case 1:
			showOTPDialog(otp);
			break;
		case 2:
			String url = "http://demo.yubico.com/php-yubico/one_factor.php?key=" + otp;
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
			break;
		case 3:
			YubicoClient client = YubicoClient.getClient(7364);
			YubicoResponse response = client.verify(otp);
			showCloudDialog(response);
			break;
		}
		
		
		return super.onContextItemSelected(item);
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