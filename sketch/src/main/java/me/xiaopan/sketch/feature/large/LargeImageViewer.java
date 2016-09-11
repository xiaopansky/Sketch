/*
 * Copyright (C) 2016 Peng fei Pan <sky@xiaopan.me>
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

package me.xiaopan.sketch.feature.large;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.util.SketchUtils;

/**
 * 大图片查看器
 */
// TODO: 16/8/29 加上旋转之后，不知道会有什么异常问题
public class LargeImageViewer {
    private static final String NAME = "LargeImageViewer";

    private Context context;
    private Callback callback;

    private boolean showTileRect;

    private float zoomScale;
    private float lastZoomScale;
    private Paint drawTilePaint;
    private Paint drawTileRectPaint;
    private Paint loadingTileRectPaint;
    private Matrix matrix;

    private boolean running;
    private Rect lastVisibleRect = new Rect();
    private TileManager tileManager;
    private TileDecodeExecutor executor;
    private String imageUri;

    public LargeImageViewer(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;

        this.matrix = new Matrix();
        this.executor = new TileDecodeExecutor(new ExecutorCallback());
        this.drawTilePaint = new Paint();
        this.tileManager = new TileManager(context.getApplicationContext(), this);
    }

    public void draw(Canvas canvas) {
        List<Tile> tileList = tileManager.getTileList();
        if (tileList != null && tileList.size() > 0) {
            int saveCount = canvas.save();
            canvas.concat(matrix);

            for (Tile tile : tileList) {
                if (!tile.isEmpty()) {
                    canvas.drawBitmap(tile.bitmap, tile.bitmapDrawSrcRect, tile.drawRect, drawTilePaint);
                    if (showTileRect) {
                        if (drawTileRectPaint == null) {
                            drawTileRectPaint = new Paint();
                            drawTileRectPaint.setColor(Color.parseColor("#88FF0000"));
                        }
                        canvas.drawRect(tile.drawRect, drawTileRectPaint);
                    }
                } else if (!tile.isDecodeParamEmpty()) {
                    if (showTileRect) {
                        if (loadingTileRectPaint == null) {
                            loadingTileRectPaint = new Paint();
                            loadingTileRectPaint.setColor(Color.parseColor("#880000FF"));
                        }
                        canvas.drawRect(tile.drawRect, loadingTileRectPaint);
                    }
                }
            }

            canvas.restoreToCount(saveCount);
        }
    }

    /**
     * 设置新的图片
     */
    public void setImage(String imageUri) {
        clean("setImage");

        this.imageUri = imageUri;
        if (!TextUtils.isEmpty(imageUri)) {
            running = true;
            executor.initDecoder(imageUri);
        } else {
            running = false;
            executor.initDecoder(null);
        }
    }

    /**
     * 更新
     */
    public void update(Matrix drawMatrix, Rect visibleRect, Point previewDrawableSize, Point imageViewSize) {
        // 没有准备好就不往下走了
        if (!isReady()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, NAME + ". not ready. " + imageUri);
            }
            return;
        }

        // 传进来的参数不能用就什么也不显示
        if (visibleRect.isEmpty() || previewDrawableSize.x == 0 || previewDrawableSize.y == 0 || imageViewSize.x == 0 || imageViewSize.y == 0) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, NAME + ". update params is empty. update" +
                        ". visibleRect=" + visibleRect.toShortString() +
                        ", previewDrawableSize=" + previewDrawableSize.x + "x" + previewDrawableSize.y +
                        ", imageViewSize=" + imageViewSize.x + "x" + imageViewSize.y +
                        ". " + imageUri);
            }
            clean("update param is empty");
            return;
        }

        // 过滤掉重复的刷新
        if (lastVisibleRect.equals(visibleRect)) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, NAME + ". visible rect no changed. update. visibleRect=" + visibleRect.toShortString() + ", oldVisibleRect=" + lastVisibleRect.toShortString() + ". " + imageUri);
            }
            return;
        }
        lastVisibleRect.set(visibleRect);

        // 如果当前完整显示预览图的话就清空什么也不显示
        if (visibleRect.width() == previewDrawableSize.x && visibleRect.height() == previewDrawableSize.y) {
            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". full display. update. " + imageUri);
            }
            clean("full display");
            return;
        }

        // 取消旧的任务并更新Matrix
        lastZoomScale = zoomScale;
        matrix.set(drawMatrix);
        zoomScale = SketchUtils.formatFloat(SketchUtils.getMatrixScale(matrix), 2);

        callback.invalidate();

        tileManager.update(visibleRect, previewDrawableSize, imageViewSize, executor.getDecoder().getImageSize());
    }

    /**
     * 清理资源（不影响继续使用）
     */
    private void clean(String why) {
        executor.cleanDecode(why);

        matrix.reset();
        lastZoomScale = 0;
        zoomScale = 0;

        tileManager.clean(why);

        callback.invalidate();
    }

    /**
     * 回收资源（回收后需要重新setImage()才能使用）
     */
    public void recycle(String why) {
        running = false;
        clean(why);
        executor.recycle(why);
        tileManager.recycle(why);
    }

    public void invalidateView(){
        callback.invalidate();
    }

    /**
     * 准备好了？
     */
    public boolean isReady() {
        return running && executor.isReady();
    }

    /**
     * 初始化中？
     */
    public boolean isInitializing() {
        return running && executor.isInitializing();
    }

    /**
     * 是否显示碎片的范围（红色表示已加载，蓝色表示正在加载）
     */
    public boolean isShowTileRect() {
        return showTileRect;
    }

    /**
     * 设置是否显示碎片的范围（红色表示已加载，蓝色表示正在加载）
     */
    @SuppressWarnings("unused")
    public void setShowTileRect(boolean showTileRect) {
        this.showTileRect = showTileRect;
        callback.invalidate();
    }

    /**
     * 获取当前缩放比例
     */
    public float getZoomScale() {
        return zoomScale;
    }

    /**
     * 获取上次的缩放比例
     */
    public float getLastZoomScale() {
        return lastZoomScale;
    }

    public TileDecodeExecutor getExecutor() {
        return executor;
    }

    public TileManager getTileManager() {
        return tileManager;
    }

    public interface Callback {
        void invalidate();
        void updateMatrix();
    }

    public interface OnTileChangedListener {
        void onTileChanged(LargeImageViewer largeImageViewer);
    }

    private class ExecutorCallback implements TileDecodeExecutor.Callback {

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void onInitCompleted() {
            if (!running) {
                if (Sketch.isDebugMode()) {
                    Log.w(Sketch.TAG, NAME + ". stop running. initCompleted");
                }
                return;
            }

            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". init completed");
            }

            callback.updateMatrix();
        }

        @Override
        public void onInitFailed(Exception e) {
            if (!running) {
                if (Sketch.isDebugMode()) {
                    Log.w(Sketch.TAG, NAME + ". stop running. initFailed");
                }
                return;
            }

            if (Sketch.isDebugMode()) {
                Log.d(Sketch.TAG, NAME + ". init failed");
            }
        }

        @Override
        public void onDecodeCompleted(Tile tile, Bitmap bitmap) {
            if (!running) {
                if (Sketch.isDebugMode()) {
                    Log.w(Sketch.TAG, NAME + ". stop running. decodeCompleted. tile=" + tile.getInfo());
                }
                bitmap.recycle();
                return;
            }

            tileManager.decodeCompleted(tile, bitmap);
        }

        @Override
        public void onDecodeFailed(Tile tile, DecodeHandler.DecodeFailedException exception) {
            if (!running) {
                if (Sketch.isDebugMode()) {
                    Log.w(Sketch.TAG, NAME + ". stop running. decodeFailed. tile=" + tile.getInfo());
                }
                return;
            }

            tileManager.decodeFailed(tile, exception);
        }
    }
}
