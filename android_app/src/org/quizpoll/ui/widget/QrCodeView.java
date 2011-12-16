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

package org.quizpoll.ui.widget;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Custom ImageView handling QR code generation using Zxing library.
 */
public class QrCodeView extends ImageView {

  private String url;

  public QrCodeView(Context context) {
    super(context, null);
  }

  public QrCodeView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
  }

  public QrCodeView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public void setUrl(String url) {
    this.url = url;
    new GenerateQrTask().execute();
  }

  private class GenerateQrTask extends AsyncTask<Void, Void, Bitmap> {

    @Override
    protected Bitmap doInBackground(Void... arg0) {
      if (url == null) {
        return null;
      }
      QRCodeWriter writer = new QRCodeWriter();
      BitMatrix matrix = null;
      try {
        matrix =
            writer.encode(url, BarcodeFormat.QR_CODE,
                QrCodeView.this.getHeight(), QrCodeView.this.getHeight());
      } catch (WriterException e) {
        e.printStackTrace();
      }
      int width = matrix.getWidth();
      int height = matrix.getHeight();
      int[] pixels = new int[width * height];
      // All are 0, or black, by default
      for (int y = 0; y < height; y++) {
        int offset = y * width;
        for (int x = 0; x < width; x++) {
          pixels[offset + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
        }
      }

      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
      return bitmap;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
      if (result != null) {
        QrCodeView.this.setImageBitmap(result);
      }
    }

  }

}
