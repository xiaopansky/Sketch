/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
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

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

import me.xiaopan.sketch.SLog;
import me.xiaopan.sketch.SLogType;
import me.xiaopan.sketch.cache.BitmapPool;
import me.xiaopan.sketch.drawable.ImageAttrs;
import me.xiaopan.sketch.drawable.SketchGifDrawable;
import me.xiaopan.sketch.drawable.SketchGifFactory;
import me.xiaopan.sketch.feature.ImageSizeCalculator;
import me.xiaopan.sketch.request.ImageFrom;
import me.xiaopan.sketch.request.LoadRequest;
import me.xiaopan.sketch.request.MaxSize;

public class ContentDataSource implements DataSource {
    protected String logName = "ContentDataSource";

    private Uri contentUri;
    private LoadRequest loadRequest;

    public ContentDataSource(Uri contentUri, LoadRequest loadRequest) {
        this.contentUri = contentUri;
        this.loadRequest = loadRequest;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return loadRequest.getContext().getContentResolver().openInputStream(contentUri);
    }

    @Override
    public SketchGifDrawable makeGifDrawable(String key, String uri, ImageAttrs imageAttrs, BitmapPool bitmapPool) {
        ContentResolver contentResolver = loadRequest.getContext().getContentResolver();
        try {
            return SketchGifFactory.createGifDrawable(key, uri, imageAttrs, bitmapPool, contentResolver, contentUri);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ImageFrom getImageFrom() {
        return ImageFrom.LOCAL;
    }

    @Override
    public void onDecodeSuccess(Bitmap bitmap, int outWidth, int outHeight, String outMimeType, int inSampleSize) {
        if (SLogType.REQUEST.isEnabled()) {
            if (bitmap != null && loadRequest.getOptions().getMaxSize() != null) {
                MaxSize maxSize = loadRequest.getOptions().getMaxSize();
                ImageSizeCalculator sizeCalculator = loadRequest.getConfiguration().getImageSizeCalculator();
                SLog.d(SLogType.REQUEST, logName, "decodeSuccess. originalSize=%dx%d, targetSize=%dx%d, " +
                                "targetSizeScale=%s, inSampleSize=%d, finalSize=%dx%d. %s",
                        outWidth, outHeight, maxSize.getWidth(), maxSize.getHeight(),
                        sizeCalculator.getTargetSizeScale(), inSampleSize, bitmap.getWidth(), bitmap.getHeight(), loadRequest.getKey());
            } else {
                SLog.d(SLogType.REQUEST, logName, "decodeSuccess. unchanged. %s", loadRequest.getKey());
            }
        }
    }

    @Override
    public void onDecodeError() {
        if (SLogType.REQUEST.isEnabled()) {
            SLog.e(SLogType.REQUEST, logName, "decode failed. %s", contentUri.toString());
        }
    }
}