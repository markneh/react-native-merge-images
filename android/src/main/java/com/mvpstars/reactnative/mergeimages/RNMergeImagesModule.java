package com.mvpstars.reactnative.mergeimages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class RNMergeImagesModule extends ReactContextBaseJavaModule {

  private static final String TAG = "RNMergeImages";

  public static final int RN_MERGE_SIZE_SMALLEST = 1;
  public static final int RN_MERGE_SIZE_LARGEST = 2;

  public static final int RN_MERGE_TARGET_TEMP = 1;
  public static final int RN_MERGE_TARGET_DISK = 2;

  public static final int DEFAULT_JPEG_QUALITY = 80;
  public static final double DEFAULT_PHOTO_HEIGHT = 2000;

  private final ReactApplicationContext reactContext;

  public RNMergeImagesModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    return Collections.unmodifiableMap(new HashMap<String, Object>() {
      {
        put("Size", getSizeConstants());
        put("Target", getTargetConstants());
      }

      private Map<String, Object> getSizeConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("smallest", RN_MERGE_SIZE_SMALLEST);
            put("largest", RN_MERGE_SIZE_LARGEST);
          }
        });
      }

      private Map<String, Object> getTargetConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("temp", RN_MERGE_TARGET_TEMP);
            put("disk", RN_MERGE_TARGET_DISK);
          }
        });
      }
    });
  }

  @ReactMethod
  public void merge(final ReadableArray images, final ReadableMap options, final Promise promise) {
    new MergeAsyncTask(images, options, promise).execute();
  }

  private class MergeAsyncTask extends AsyncTask<Void, Void, Void> {
    private final ReadableArray images;
    private final ReadableMap options;
    private final Promise promise;

    public MergeAsyncTask(final ReadableArray images, final ReadableMap options, final Promise promise) {
      this.images = images;
      this.options = options;
      this.promise = promise;
    }

    @Override
    protected Void doInBackground(Void... voids) {
      try {
        final int maxWidth = options.hasKey("maxWidth") ? options.getInt("maxWidth") : 0;
        final int target = options.hasKey("target") ? options.getInt("target") : RN_MERGE_TARGET_TEMP;
        final int jpegQuality = options.hasKey("jpegQuality") ? options.getInt("jpegQuality") : DEFAULT_JPEG_QUALITY;

        final ArrayList<BitmapMetadata> bitmaps = new ArrayList<>(images.size());
        int targetWidth = 0;
        int targetHeight = 0;

        if (images.size() == 0) {
          throw new Exception("Provided arrays of image paths is empty");
        }

        for (int i = 0, n = images.size(); i < n; i++) {
          String path = images.getString(i);
          BitmapMetadata bitmapMetadata = BitmapMetadata.load(getFilePath(path));
          if (bitmapMetadata != null) {
            bitmaps.add(bitmapMetadata);

            Size originalSize = new Size(bitmapMetadata.width, bitmapMetadata.height);
            Size clampedSize = clampedImageSize(originalSize, maxWidth);

            targetHeight += clampedSize.height;
            if (targetWidth < clampedSize.width) {
              targetWidth = clampedSize.width;
            }
          }
        }

        final Bitmap mergedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(mergedBitmap);

        float y = 0;

        for (BitmapMetadata bitmapMetadata : bitmaps) {
          Bitmap bitmap = BitmapFactory.decodeFile(bitmapMetadata.fileName);

          Size originalSize = new Size(bitmapMetadata.width, bitmapMetadata.height);
          Size clampedSize = clampedImageSize(originalSize, maxWidth);

          canvas.drawBitmap(bitmap, null, new RectF(0, y, clampedSize.width, clampedSize.height + y), null);
          y += clampedSize.height;

          bitmap.recycle();
        }

        saveBitmap(mergedBitmap, target, jpegQuality, promise);
      } catch (Exception e) {
        promise.reject("Failed to merge images", e);
      }
      return null;
    }
  }

  private Size clampedImageSize(Size size, int maxWidth) {
    if (maxWidth <= 0) {
      return size;
    }

    float aspectRatio = (float)size.width / (float)size.height;
    int width = Math.min(size.width, maxWidth);
    int height = (int)((float)width / aspectRatio);

    Size clampedSize = new Size(width, height);
    return clampedSize;
  }

  private class Size {
    public final int width;
    public final int height;

    public Size(int width, int height) {
      this.width = width;
      this.height = height;
    }
  }

  private class MergeImagesValidationException extends RuntimeException {
    public final String message;

    public MergeImagesValidationException(String message, Throwable cause) {
      super(message, cause);

      this.message = message;
    }
  }

  private static String getFilePath(String file) {
    try {
      final String uriPath = Uri.parse(file).getPath();
      return (uriPath != null ? uriPath : file);
    } catch (RuntimeException e) {
      return file;
    }
  }

  private void saveBitmap(Bitmap bitmap, int target, int jpegQuality, Promise promise) {
    try {
      File file;
      switch (target) {
        case RN_MERGE_TARGET_DISK:
          file = getDiskFile();
          break;
        default:
          file = getTempFile();
      }
      final FileOutputStream out = new FileOutputStream(file);
      bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
      WritableMap response = new WritableNativeMap();
      response.putString("path", Uri.fromFile(file).toString());
      response.putInt("width", bitmap.getWidth());
      response.putInt("height", bitmap.getHeight());
      promise.resolve(response);
      out.flush();
      out.close();
    } catch (Exception e) {
      promise.reject("Failed to save image file", e);
    }
  }

  private File getDiskFile() throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File outputDir = reactContext.getFilesDir();
    outputDir.mkdirs();
    return new File(outputDir, "IMG_" + timeStamp + ".jpg");
  }

  private File getTempFile() throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File outputDir = reactContext.getCacheDir();
    File outputFile = File.createTempFile("IMG_" + timeStamp, ".jpg", outputDir);
    return outputFile;
  }
}
