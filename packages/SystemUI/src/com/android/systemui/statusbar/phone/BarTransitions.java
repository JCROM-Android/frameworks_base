/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

import android.os.SystemProperties;
import java.io.File;
import android.content.Context;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.os.Environment;
import android.view.Display;
import android.view.WindowManager;
import android.view.Surface;
import android.graphics.drawable.ColorDrawable;

import com.android.systemui.R;

public class BarTransitions {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_COLORS = false;

    public static final boolean HIGH_END = ActivityManager.isHighEndGfx();

    public static final int MODE_OPAQUE = 0;
    public static final int MODE_SEMI_TRANSPARENT = 1;
    public static final int MODE_TRANSLUCENT = 2;
    public static final int MODE_LIGHTS_OUT = 3;
    public static final int MODE_INIT = -1;

    public static final int LIGHTS_IN_DURATION = 250;
    public static final int LIGHTS_OUT_DURATION = 750;
    public static final int BACKGROUND_DURATION = 200;

    private String mTag;
    private View mView;
    private boolean mSupportsTransitions = ActivityManager.isHighEndGfx();
    private BarBackgroundDrawable mBarBackground;

    private int mMode;

    public void initBarTransitions(View view, int gradientResourceId) {
        mTag = "BarTransitions." + view.getClass().getSimpleName();
        mView = view;
        mBarBackground = new BarBackgroundDrawable(mView.getContext(), gradientResourceId);
        if (HIGH_END) {
            mView.setBackground(mBarBackground);
        }
    }

    public BarTransitions(View view, int gradientResourceId) {
        initBarTransitions(view, gradientResourceId);
    }

    public int getMode() {
        return mMode;
    }

    public void refresh(int mode, boolean animate) {
        transitionTo(mode, animate);
    }

    public void transitionTo(int mode, boolean animate) {
        // low-end devices do not support translucent modes, fallback to opaque
        if (!HIGH_END && (mode == MODE_SEMI_TRANSPARENT || mode == MODE_TRANSLUCENT)) {
            mode = MODE_OPAQUE;
        }
        if (mMode == mode) return;
        int oldMode = mMode;
        mMode = mode;
        if (DEBUG) Log.d(mTag, String.format("%s -> %s animate=%s",
                modeToString(oldMode), modeToString(mode),  animate));
        onTransition(oldMode, mMode, animate);
    }

    protected void onTransition(int oldMode, int newMode, boolean animate) {
        if (HIGH_END) {
            applyModeBackground(oldMode, newMode, animate);
        }
    }

    protected void applyModeBackground(int oldMode, int newMode, boolean animate) {
        if (DEBUG) Log.d(mTag, String.format("applyModeBackground oldMode=%s newMode=%s animate=%s",
                modeToString(oldMode), modeToString(newMode), animate));
        mBarBackground.applyModeBackground(oldMode, newMode, animate);
    }

    public static String modeToString(int mode) {
        if (mode == MODE_OPAQUE) return "MODE_OPAQUE";
        if (mode == MODE_SEMI_TRANSPARENT) return "MODE_SEMI_TRANSPARENT";
        if (mode == MODE_TRANSLUCENT) return "MODE_TRANSLUCENT";
        if (mode == MODE_LIGHTS_OUT) return "MODE_LIGHTS_OUT";
        if ((DEBUG) && (mode == MODE_INIT)) return "MODE_INIT";
        throw new IllegalArgumentException("Unknown mode " + mode);
    }

    public void finishAnimations() {
        mBarBackground.finishAnimation();
    }

    public void setContentVisible(boolean visible) {
        // for subclasses
    }

    private static class BarBackgroundDrawable extends Drawable {
        private int mOpaque;
        private int mSemiTransparent;
        private Drawable mGradient;
        private TimeInterpolator mInterpolator;

        private int mMode = -1;
        private boolean mAnimating;
        private long mStartTime;
        private long mEndTime;

        private int mGradientAlpha;
        private int mColor;

        private int mGradientAlphaStart;
        private int mColorStart;

        public BarBackgroundDrawable(Context context, int gradientResourceId) {
            final Resources res = context.getResources();
            if (DEBUG_COLORS) {
                mOpaque = 0xff0000ff;
                mSemiTransparent = 0x7f0000ff;
            } else {
                mOpaque = res.getColor(R.color.system_bar_background_opaque);
                mSemiTransparent = res.getColor(R.color.system_bar_background_semi_transparent);
            }
            String gradientStr = SystemProperties.get("persist.sys.prop.gradient");
            if(!gradientStr.equals("true")) {
                mGradient = res.getDrawable(gradientResourceId);
            } else {
                mGradient = new ColorDrawable(res.getColor(R.color.system_bar_background_transparent));
            }
            mInterpolator = new LinearInterpolator();
        }

        @Override
        public void setAlpha(int alpha) {
            // noop
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // noop
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mGradient.setBounds(bounds);
        }

        public void applyModeBackground(int oldMode, int newMode, boolean animate) {
            if (mMode == newMode) return;
            mMode = newMode;
            mAnimating = animate;
            if (animate) {
                long now = SystemClock.elapsedRealtime();
                mStartTime = now;
                mEndTime = now + BACKGROUND_DURATION;
                mGradientAlphaStart = mGradientAlpha;
                mColorStart = mColor;
            }
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        public void finishAnimation() {
            if (mAnimating) {
                mAnimating = false;
                invalidateSelf();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            int targetGradientAlpha = 0, targetColor = 0;
            if (mMode == MODE_TRANSLUCENT) {
                targetGradientAlpha = 0xff;
            } else if (mMode == MODE_SEMI_TRANSPARENT) {
                targetColor = mSemiTransparent;
            } else {
                targetColor = mOpaque;
            }
            if (!mAnimating) {
                mColor = targetColor;
                mGradientAlpha = targetGradientAlpha;
            } else {
                final long now = SystemClock.elapsedRealtime();
                if (now >= mEndTime) {
                    mAnimating = false;
                    mColor = targetColor;
                    mGradientAlpha = targetGradientAlpha;
                } else {
                    final float t = (now - mStartTime) / (float)(mEndTime - mStartTime);
                    final float v = Math.max(0, Math.min(mInterpolator.getInterpolation(t), 1));
                    mGradientAlpha = (int)(v * targetGradientAlpha + mGradientAlphaStart * (1 - v));
                    mColor = Color.argb(
                          (int)(v * Color.alpha(targetColor) + Color.alpha(mColorStart) * (1 - v)),
                          (int)(v * Color.red(targetColor) + Color.red(mColorStart) * (1 - v)),
                          (int)(v * Color.green(targetColor) + Color.green(mColorStart) * (1 - v)),
                          (int)(v * Color.blue(targetColor) + Color.blue(mColorStart) * (1 - v)));
                }
            }
            if (mGradientAlpha > 0) {
                mGradient.setAlpha(mGradientAlpha);
                mGradient.draw(canvas);
            }
            if (Color.alpha(mColor) > 0) {
                canvas.drawColor(mColor);
            }
            if (mAnimating) {
                invalidateSelf();  // keep going
            }
        }
    }

    protected Drawable getDrawableFromFile(String DIR, String MY_FILE_NAME) {
        StringBuilder builder = new StringBuilder();
        builder.append(Environment.getDataDirectory().toString() + "/theme/"+DIR+"/");
        builder.append(File.separator);
        builder.append(MY_FILE_NAME);
        String filePath = builder.toString();
        String extension = checkThemeFile(filePath);
        if(extension != null) {
            return Drawable.createFromPath(filePath + extension);
        }else {
            return null;
        }
    }

    private String checkThemeFile(String filename) {
        String extension = null;
        File file = null;

        file = new File(filename + ".png");
        if(file.exists()) {
            extension = ".png";
        }else {
            file = new File(filename + ".jpg");
            if(file.exists()) {
                extension = ".jpg";
            }
        }

        return extension;
    }

    protected boolean requiresRotation() {
        WindowManager wm = (WindowManager) mView.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display dp = wm.getDefaultDisplay();

        return dp.getRotation()==Surface.ROTATION_90 || dp.getRotation()==Surface.ROTATION_270;
    }

}
