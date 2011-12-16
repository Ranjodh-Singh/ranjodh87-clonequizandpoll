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

package org.quizpoll.util;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.quizpoll.R;
import org.quizpoll.ui.HomeActivity;

/**
 * A class that handles some common activity-related functionality in the app,
 * such as setting up the action bar. Taken from Google IO 2011 app and
 * modified.
 */
public class ActivityHelper {
  private final Activity activity;
  private TextView titleText;

  public ActivityHelper(Activity activity) {
    this.activity = activity;
  }

  /**
   * Invoke "home" action, returning to HomeActivity.
   */
  public void goHome() {
    if (activity instanceof HomeActivity) {
      return;
    }

    final Intent intent = new Intent(activity, HomeActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    activity.startActivity(intent);
  }

  public void setupActionBar(String title) {
    setupActionBar(title, true);
  }

  /**
   * Sets up the action bar with the given title and accent color. If title is
   * null, then the app logo will be shown instead of a title. Otherwise, a home
   * button and title are visible. If color is null, then the default colorstrip
   * is visible.
   */
  public void setupActionBar(String title, boolean showHomeButton) {
    final ViewGroup actionBarCompat = getActionBarCompat();
    if (actionBarCompat == null) {
      return;
    }

    LinearLayout.LayoutParams springLayoutParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.FILL_PARENT);
    springLayoutParams.weight = 1;

    View.OnClickListener homeClickListener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        goHome();
      }
    };

    if (title != null) {
      // Add Home button
      if (showHomeButton) {
        addActionButtonCompat(R.drawable.ic_title_home, homeClickListener, true);
      }

      // Add title text
      titleText = new TextView(activity, null, R.attr.actionbarCompatTextStyle);
      titleText.setLayoutParams(springLayoutParams);
      titleText.setText(title);
      actionBarCompat.addView(titleText);

    } else {
      // Add app name
      titleText = new TextView(activity, null, R.attr.actionbarCompatTextStyle);
      titleText.setLayoutParams(springLayoutParams);
      titleText.setText(R.string.app_name);
      actionBarCompat.addView(titleText);

      // Add logo
      //ImageButton logo = new ImageButton(activity, null, R.attr.actionbarCompatLogoStyle);
      //logo.setOnClickListener(homeClickListener);
      //actionBarCompat.addView(logo);

      // Add spring (dummy view to align future children to the right)
      View spring = new View(activity);
      spring.setLayoutParams(springLayoutParams);
      actionBarCompat.addView(spring);
    }
  }

  /**
   * Change title later - needs to be initialized using setupActionBar
   */
  public void changeTitle(String title) {
    if (titleText != null) {
      titleText.setText(title);
    }
  }

  /**
   * Returns the {@link ViewGroup} for the action bar on phones (compatibility
   * action bar). Can return null, and will return null on Honeycomb.
   */
  public ViewGroup getActionBarCompat() {
    return (ViewGroup) activity.findViewById(R.id.actionbar_compat);
  }

  /**
   * Adds an action bar button to the compatibility action bar (on phones).
   */
  public View addActionButtonCompat(int iconResId, View.OnClickListener clickListener,
      boolean separatorAfter) {
    final ViewGroup actionBar = getActionBarCompat();
    if (actionBar == null) {
      return null;
    }

    // Create the separator
    ImageView separator = new ImageView(activity, null, R.attr.actionbarCompatSeparatorStyle);
    separator.setLayoutParams(new ViewGroup.LayoutParams(2, ViewGroup.LayoutParams.FILL_PARENT));

    // Create the button
    ImageButton actionButton = new ImageButton(activity, null, R.attr.actionbarCompatButtonStyle);
    actionButton.setLayoutParams(new ViewGroup.LayoutParams((int) activity.getResources()
        .getDimension(R.dimen.actionbar_compat_height), ViewGroup.LayoutParams.FILL_PARENT));
    actionButton.setImageResource(iconResId);
    actionButton.setScaleType(ImageView.ScaleType.CENTER);
    actionButton.setOnClickListener(clickListener);

    // Add separator and button to the action bar in the desired order

    if (!separatorAfter) {
      actionBar.addView(separator);
    }

    actionBar.addView(actionButton);

    if (separatorAfter) {
      actionBar.addView(separator);
    }

    return actionButton;
  }
}
