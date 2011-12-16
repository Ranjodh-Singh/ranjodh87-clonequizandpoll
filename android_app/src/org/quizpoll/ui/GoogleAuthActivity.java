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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.quizpoll.R;
import org.quizpoll.data.Preferences;
import org.quizpoll.data.Preferences.PrefType;
import org.quizpoll.net.AppEngineHelper;
import org.quizpoll.net.HttpHelper;
import org.quizpoll.net.HttpListener;
import org.quizpoll.util.Utils;

import java.io.IOException;

/**
 * Handles authentication to Google Account against AppEngine instance. It uses
 * ClientLogin and built-in Android auth. It's also used for authenticating
 * against Google Docs API.
 */
public class GoogleAuthActivity extends Activity {

  /**
   * Auth server types
   */
  public static final int AUTHSERVER_APPENGINE = 0;
  public static final int AUTHSERVER_DOCS = 1;

  @SuppressWarnings("unused")
  private static final String TAG = "GoogleAuthActivity";
  private static final int REQUEST_AUTHENTICATE = 0;
  private static final String AUTHTYPE_APPENGINE = "ah"; // AppEngine
  private static final String AUTHTYPE_DOCS_LIST = "writely"; // Google Docs
                                                              // List
  private static String email;
  private String authType;
  private Listener listener;
  /**
   * Allows to renew token for each authserver just once for one session. It
   * prevents infinite renewing in case of some error.
   */
  private boolean[] renewedToken = new boolean[] {false, false, false};

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_AUTHENTICATE) {
      if (resultCode == RESULT_OK) {
        start(false, listener);
      } else {
        listener.onFailure();
      }
    }
  }

  public void authenticatedRequest(final int server, final HttpHelper helper) {
    authenticatedRequest(server, helper, false);
  }

  /**
   * Authenticates against server if needed and then runs the request
   */
  public void authenticatedRequest(final int server, final HttpHelper helper,
      final boolean invalidToken) {
    if (invalidToken) {
      // Prevent never ending token renewals in case of errors, only one renewal
      // per session is permitted
      if (renewedToken[server]) {
        Toast.makeText(GoogleAuthActivity.this, R.string.auth_failed, Toast.LENGTH_SHORT).show();
        return;
      } else {
        renewedToken[server] = true;
      }
    }
    if (email == null) {
      email = Utils.getGoogleAccount(this);
    }
    if (email == null) {
      Toast.makeText(this, R.string.google_account_required, Toast.LENGTH_LONG).show();
    } else {
      boolean needsAuth = true;
      switch (server) {
        case AUTHSERVER_APPENGINE:
          authType = AUTHTYPE_APPENGINE;
          needsAuth = (Preferences.getString(PrefType.COOKIE_APPENGINE, this) == null);
          break;
        case AUTHSERVER_DOCS:
          authType = AUTHTYPE_DOCS_LIST;
          needsAuth = (Preferences.getString(PrefType.AUTH_TOKEN_DOCS, this) == null);
          break;
      }
      if (invalidToken) {
        needsAuth = true;
      }
      if (!needsAuth) {
        helper.start();
      } else {
        start(invalidToken, new Listener() {

          @Override
          public void onSuccess() {
            switch (server) {
              case AUTHSERVER_APPENGINE:
                new AppEngineHelper(AppEngineHelper.LOGIN, Preferences.getString(
                    PrefType.AUTH_TOKEN_AE, GoogleAuthActivity.this), true,
                    GoogleAuthActivity.this, new HttpListener() {

                      @Override
                      public void onSuccess(Object responseData) {
                        Preferences.add(PrefType.COOKIE_APPENGINE, (String) responseData,
                            GoogleAuthActivity.this);
                        helper.start();
                      }
                    });
                break;
              case AUTHSERVER_DOCS:
                helper.start();
                break;
            }
          }

          @Override
          public void onFailure() {
            Toast.makeText(GoogleAuthActivity.this, R.string.auth_failed, Toast.LENGTH_SHORT)
                .show();
          }
        });
      }
    }
  }

  /**
   * Starts auth process.
   */
  private void start(boolean tokenExpired, Listener listener) {
    this.listener = listener;
    AccountManager manager = AccountManager.get(this);
    if (tokenExpired) {
      if (authType.equals(AUTHTYPE_APPENGINE)) {
        manager.invalidateAuthToken("com.google",
            Preferences.getString(PrefType.AUTH_TOKEN_AE, this));
      } else if (authType.equals(AUTHTYPE_DOCS_LIST)) {
        manager.invalidateAuthToken("com.google",
            Preferences.getString(PrefType.AUTH_TOKEN_DOCS, this));
      }
    }
    Account[] accounts = manager.getAccountsByType("com.google");
    for (Account account : accounts) {
      if (account.name.trim().equals(email.trim())) {
        authenticate(manager, account);
        break;
      }
    }
  }

  /**
   * Authenticate using native Android auth mechanism.
   */
  private void authenticate(final AccountManager manager, final Account account) {
    manager.getAuthToken(account, authType, null, this, new AccountManagerCallback<Bundle>() {
      @Override
      public void run(AccountManagerFuture<Bundle> future) {
        Bundle bundle;
        try {
          bundle = future.getResult();
        } catch (OperationCanceledException e) {
          bundle = null;
        } catch (AuthenticatorException e) {
          bundle = null;
        } catch (IOException e) {
          bundle = null;
        }
        if (bundle == null) {
          listener.onFailure();
          return;
        }
        if (bundle.containsKey(AccountManager.KEY_INTENT)) {
          Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
          int flags = intent.getFlags();
          flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
          intent.setFlags(flags);
          startActivityForResult(intent, REQUEST_AUTHENTICATE);
        } else if (bundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
          handleAuthToken(bundle.getString(AccountManager.KEY_AUTHTOKEN));
        }
      }
    }, null);
  }

  /**
   * Finish auth process - save auth token
   */
  private void handleAuthToken(String authToken) {
    Preferences.add(PrefType.USER_EMAIL, email, this);
    if (authType.equals(AUTHTYPE_APPENGINE)) {
      Preferences.add(PrefType.AUTH_TOKEN_AE, authToken, this);
      listener.onSuccess();
    } else if (authType.equals(AUTHTYPE_DOCS_LIST)) {
      Preferences.add(PrefType.AUTH_TOKEN_DOCS, authToken, this);
      listener.onSuccess();
    }
  }

  /**
   * Listener for google auth
   */
  public interface Listener {
    public void onSuccess();

    public void onFailure();
  }
}
