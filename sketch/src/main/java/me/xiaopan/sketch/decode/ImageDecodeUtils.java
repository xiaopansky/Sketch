/*
 * Copyright (C) 2017 Peng fei Pan <sky@xiaopan.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch.decode;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import me.xiaopan.sketch.ErrorTracker;
import me.xiaopan.sketch.SLog;
import me.xiaopan.sketch.cache.BitmapPool;
import me.xiaopan.sketch.cache.BitmapPoolUtils;
import me.xiaopan.sketch.cache.DiskCache;
import me.xiaopan.sketch.datasource.DataSource;
import me.xiaopan.sketch.datasource.DiskCacheDataSource;
import me.xiaopan.sketch.datasource.FileDataSource;
import me.xiaopan.sketch.request.LoadRequest;
import me.xiaopan.sketch.request.MaxSize;
import me.xiaopan.sketch.util.SketchUtils;

public class ImageDecodeUtils {

    public static Bitmap decodeBitmap(DataSource dataSource, BitmapFactory.Options options) throws IOException {
        InputStream inputStream = null;
        Bitmap bitmap = null;

        try {
            inputStream = dataSource.getInputStream();
            bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        } finally {
            SketchUtils.close(inputStream);
        }

        return bitmap;
    }

    public static Bitmap decodeRegionBitmap(DataSource dataSource, Rect srcRect, BitmapFactory.Options options) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1) {
            return null;
        }

        InputStream inputStream;
        try {
            inputStream = dataSource.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        BitmapRegionDecoder regionDecoder;
        try {
            regionDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            SketchUtils.close(inputStream);
        }

        Bitmap bitmap = regionDecoder.decodeRegion(srcRect, options);
        regionDecoder.recycle();
        SketchUtils.close(inputStream);
        return bitmap;
    }

    static void decodeSuccess(@NonNull Bitmap bitmap, int outWidth, int outHeight, int inSampleSize, LoadRequest loadRequest, String logName) {
        if (SLog.isLoggable(SLog.LEVEL_DEBUG | SLog.TYPE_FLOW)) {
            if (loadRequest.getOptions().getMaxSize() != null) {
                MaxSize maxSize = loadRequest.getOptions().getMaxSize();
                ImageSizeCalculator sizeCalculator = loadRequest.getConfiguration().getSizeCalculator();
                SLog.d(logName,
                        "Decode bitmap. originalSize=%dx%d, targetSize=%dx%d, targetSizeScale=%s, inSampleSize=%d, finalSize=%dx%d. %s",
                        outWidth, outHeight, maxSize.getWidth(), maxSize.getHeight(), sizeCalculator.getTargetSizeScale(),
                        inSampleSize, bitmap.getWidth(), bitmap.getHeight(), loadRequest.getKey());
            } else {
                SLog.d(logName, "Decode bitmap. bitmapSize=%dx%d. %s", bitmap.getWidth(), bitmap.getHeight(), loadRequest.getKey());
            }
        }
    }

    static void decodeError(LoadRequest loadRequest, DataSource dataSource, String logName, String cause, Throwable tr) {
        if (tr != null) {
            SLog.e(logName, Log.getStackTraceString(tr));
        }

        if (dataSource instanceof DiskCacheDataSource) {
            DiskCache.Entry diskCacheEntry = ((DiskCacheDataSource) dataSource).getDiskCacheEntry();
            File cacheFile = diskCacheEntry.getFile();
            if (diskCacheEntry.delete()) {
                SLog.e(logName, "Decode failed. %s. Disk cache deleted. fileLength=%d. %s", cause, cacheFile.length(), loadRequest.getKey(), tr);
            } else {
                SLog.e(logName, "Decode failed. %s. Disk cache can not be deleted. fileLength=%d. %s", cause, cacheFile.length(), loadRequest.getKey());
            }
        } else if (dataSource instanceof FileDataSource) {
            File file = ((FileDataSource) dataSource).getFile(null, null);
            SLog.e(logName, "Decode failed. %s. filePath=%s, fileLength=%d. %s",
                    cause, file.getPath(), file.exists() ? file.length() : -1);
        } else {
            SLog.e(logName, "Decode failed. %s. %s", cause, loadRequest.getUri());
        }
    }

    /**
     * 通过异常类型以及message确定是不是由inBitmap导致的解码失败
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static boolean isInBitmapDecodeError(Throwable throwable, BitmapFactory.Options options, boolean fromBitmapRegionDecoder) {
        if (fromBitmapRegionDecoder) {
            if (!BitmapPoolUtils.sdkSupportInBitmapForRegionDecoder()) {
                return false;
            }
        } else {
            if (!BitmapPoolUtils.sdkSupportInBitmap()) {
                return false;
            }
        }

        if (!(throwable instanceof IllegalArgumentException)) {
            return false;
        }

        if (options.inBitmap == null) {
            return false;
        }

        String message = throwable.getMessage();
        return message != null && (message.equals("Problem decoding into existing bitmap") || message.contains("bitmap"));
    }

    /**
     * 反馈inBitmap解码失败，并回收inBitmap
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void recycleInBitmapOnDecodeError(ErrorTracker errorTracker, BitmapPool bitmapPool,
                                                    String imageUri, int imageWidth, int imageHeight, String imageMimeType,
                                                    Throwable throwable, BitmapFactory.Options decodeOptions, boolean fromBitmapRegionDecoder) {
        if (fromBitmapRegionDecoder) {
            if (!BitmapPoolUtils.sdkSupportInBitmapForRegionDecoder()) {
                return;
            }
        } else {
            if (!BitmapPoolUtils.sdkSupportInBitmap()) {
                return;
            }
        }

        errorTracker.onInBitmapDecodeError(imageUri, imageWidth, imageHeight, imageMimeType, throwable, decodeOptions.inSampleSize, decodeOptions.inBitmap);

        BitmapPoolUtils.freeBitmapToPool(decodeOptions.inBitmap, bitmapPool);
        decodeOptions.inBitmap = null;
    }

    /**
     * 通过异常类型以及message确定是不是由srcRect导致的解码失败
     */
    public static boolean isSrcRectDecodeError(Throwable throwable, int imageWidth, int imageHeight, Rect srcRect) {
        if (!SketchUtils.sdkSupportBitmapRegionDecoder()) {
            return false;
        }

        if (!(throwable instanceof IllegalArgumentException)) {
            return false;
        }

        if (srcRect.left < imageWidth || srcRect.top < imageHeight || srcRect.right > imageWidth || srcRect.bottom > imageHeight) {
            return true;
        }

        String message = throwable.getMessage();
        return message != null && (message.equals("rectangle is outside the image srcRect") || message.contains("srcRect"));
    }
}
