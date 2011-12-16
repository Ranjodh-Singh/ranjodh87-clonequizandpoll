/*
 Copyright 2011 Google Inc. All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License');
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS-IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package org.quizpoll.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.quizpoll.R;
import org.quizpoll.net.HttpHelper;
import org.quizpoll.net.HttpListener;
import org.quizpoll.net.UrlShortenerHelper;
import org.quizpoll.ui.widget.QrCodeView;
import org.quizpoll.util.ActivityHelper;

import java.io.IOException;

/**
 * Allows sharing of URL using NFC, NFC stickers, QR codes of e-mail. Supports
 * Quiz Games and Polls. Uses URL shortener for links in NFC and QR.
 */
public class ShareActivity extends Activity {

  @SuppressWarnings("unused")
  private static final String TAG = "ShareActivity";
  public static final String EXTRA_URL = "org.quizpoll.Url";
  public static final String EXTRA_QUIZ_NAME = "org.quizpoll.QuizName";
  public static final String EXTRA_POLL_NAME = "org.quizpoll.PollName";
  static final int DIALOG_NFC_STICKER = 0;
  private boolean nfcBroadcasting = false;
  private boolean nfcAvailable = false;
  private NfcAdapter nfcAdapter;
  private ActivityHelper helper;
  private String originalUrl;
  private String shortenedUrl;
  private String quizName;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_share);
    helper = new ActivityHelper(this);
    // Shorten URL and start sharing
    originalUrl = getIntent().getStringExtra(EXTRA_URL);
    shortenUrl(originalUrl);
    quizName = getIntent().getStringExtra(EXTRA_QUIZ_NAME);
    if (quizName != null) {
      helper.setupActionBar(getString(R.string.share_quiz));
    } else {
      quizName = getIntent().getStringExtra(EXTRA_POLL_NAME);
      helper.setupActionBar(getString(R.string.share_poll));
    }
    ((TextView) findViewById(R.id.content_title)).setText(quizName);
    helper.addActionButtonCompat(R.drawable.ic_title_send, new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        shareByEmail();
      }
    }, false);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // If NFC is supported, start broadcasting
    if (nfcAvailable && !nfcBroadcasting) {
      nfcBroadcasting = true;
      enableNdefExchangeMode();
    }
  }

  @Override
  protected void onPause() {
    if (nfcBroadcasting) {
      disableNdefExchangeMode();
      nfcBroadcasting = false;
    }
    super.onPause();
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    if (id == DIALOG_NFC_STICKER) {
      // Show UI
      AlertDialog.Builder alert = new AlertDialog.Builder(ShareActivity.this);
      alert.setTitle(R.string.write_to_sticker);
      alert.setMessage(R.string.nfc_sticker_message);
      alert.setNegativeButton(R.string.cancel, new OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
          dialog.cancel();
        }
      });
      alert.setOnCancelListener(new OnCancelListener() {

        @Override
        public void onCancel(DialogInterface dialog) {
          disableTagWriteMode();
        }
      });
      enableTagWriteMode();
      return alert.create();
    }
    return null;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    // Tag writing mode
    if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
      Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
      writeTag(encodeUrlAsNdef(), detectedTag);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.share_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.write:
        if (nfcAvailable) {
          showDialog(DIALOG_NFC_STICKER);
        }
        return true;
      case R.id.send:
        shareByEmail();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * Shares quiz or session via e-mail
   */
  private void shareByEmail() {
    Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
    shareIntent.setType("text/plain");
    String subject;
    subject = getString(R.string.email_subject_quiz, quizName);

    shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT,
        getString(R.string.email_text, originalUrl));
    startActivity(shareIntent);
  }

  /**
   * Starts broadcasting NFC and enables QR code
   */
  private void startSharing(String url) {
    this.shortenedUrl = url;
    ((QrCodeView) findViewById(R.id.qr_code)).setUrl(url);
    // Setup NFC
    if (Build.VERSION.SDK_INT >= 10
        && getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
      enableNfcFunctions(helper);
      nfcAvailable = true;
      onResume();
    } else {
      // NFC not available
      ((ProgressBar) findViewById(R.id.nfc_progress)).setIndeterminate(false);
      ((TextView) findViewById(R.id.nfc_text)).setText(R.string.nfc_not_available);
    }
  }

  /**
   * Try to shorten URL for marking attendance and show attendance tracking
   * activity. Shortening is needed, because current URLs exceeds memory of NFC
   * stickers. It also makes NFC and QR scanning much faster.
   */
  private void shortenUrl(final String url) {
    new UrlShortenerHelper(HttpHelper.SINGLE_MESSAGE_TYPE, url, true, this, new HttpListener() {

      @Override
      public void onSuccess(Object responseData) {
        // Use shortened URL
        startSharing((String) responseData);
      }

      @Override
      public void onFailure(int errorCode) {
        // Use full URL without shortener
        startSharing(url);
      }
    });
  }

  /**
   * Enable specific functionality for phones which support NFC.
   */
  private void enableNfcFunctions(ActivityHelper helper) {
    nfcAdapter = NfcAdapter.getDefaultAdapter(this);
    // Button in actionbar for writing to NFC sticker
    helper.addActionButtonCompat(R.drawable.ic_title_write, new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        showDialog(DIALOG_NFC_STICKER);
      }
    }, false);
  }

  /**
   * Starts to broadcast URL to any NFC-capable device nearby.
   */
  private void enableNdefExchangeMode() {
    nfcAdapter.enableForegroundNdefPush(this, encodeUrlAsNdef());
  }

  /**
   * Encodes attendance tracking URL in NDEF format for NFC.
   */
  private NdefMessage encodeUrlAsNdef() {
    NdefRecord textRecord =
        new NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, NdefRecord.RTD_URI, new byte[0],
            shortenedUrl.getBytes());
    return new NdefMessage(new NdefRecord[] {textRecord});
  }

  /**
   * Stops URL broadcasting.
   */
  private void disableNdefExchangeMode() {
    nfcAdapter.disableForegroundNdefPush(this);
  }

  /**
   * Starts writing to any writable NFC tag nearby.
   */
  private void enableTagWriteMode() {
    IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
    IntentFilter[] writeTagFilters = new IntentFilter[] {tagDetected};
    PendingIntent nfcPendingIntent =
        PendingIntent.getActivity(this, 0,
            new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, writeTagFilters, null);
  }

  /**
   * Stops writing to NFC tags.
   */
  private void disableTagWriteMode() {
    nfcAdapter.disableForegroundDispatch(this);
  }

  /**
   * Actually writes data into tag (sticker).
   */
  private void writeTag(NdefMessage message, Tag tag) {
    int size = message.toByteArray().length;

    try {
      Ndef ndef = Ndef.get(tag);
      if (ndef != null) {
        ndef.connect();

        if (!ndef.isWritable()) {
          toast(R.string.tag_read_only);
          return;
        }
        if (ndef.getMaxSize() < size) {
          toast(R.string.exceeded_tag_capacity);
          return;
        }

        ndef.writeNdefMessage(message);
        toast(R.string.sticker_write_success);
      } else {
        NdefFormatable format = NdefFormatable.get(tag);
        if (format != null) {
          try {
            format.connect();
            format.format(message);
            toast(R.string.sticker_write_success);
          } catch (IOException e) {
            toast(R.string.sticker_write_error);
          }
        } else {
          toast(R.string.sticker_write_error);
        }
      }
    } catch (FormatException e) {
      toast(R.string.sticker_write_error);
    } catch (IOException e) {
      toast(R.string.sticker_write_error);
    }
  }

  private void toast(int text) {
    Toast.makeText(this, getString(text), Toast.LENGTH_LONG).show();
    dismissDialog(DIALOG_NFC_STICKER);
  }

}
