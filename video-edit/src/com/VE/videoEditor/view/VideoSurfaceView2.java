/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.VE.videoEditor.view;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;

import com.VE.videoEditor.*;
import com.VE.videoEditor.Effect.interfaces.ShaderInterface;
import com.VE.videoEditor.Effect.interfaces.VideoEffect;

public class VideoSurfaceView2 extends GLSurfaceView {
    private static final String TAG = "VideoSurfaceView";
    private static final int SLEEP_TIME_MS = 1000;

    private VideoRender mRenderer;
    private ShaderInterface mEffect;
    //private MediaPlayer mMediaPlayer = null;

    public VideoSurfaceView2(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        mRenderer = new VideoRender(context);
        setRenderer(mRenderer);
    }

    /**
     * A GLSurfaceView implementation that wraps TextureRender.  Used to render frames from a
     * video decoder to a View.
     */
    private static class VideoRender
            implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
        private static String TAG = "VideoRender";

        private TextureRender mTextureRender;
        private SurfaceTexture mSurfaceTexture;
        private boolean updateSurface = false;
        private String mShader;

        public VideoRender(Context context) {
            mTextureRender = new TextureRender();
        }

        public void onDrawFrame(GL10 glUnused) {
            synchronized(this) {
                if (updateSurface) {
                    mSurfaceTexture.updateTexImage();
                    updateSurface = false;
                }

                if (mShader != null) {
                    mTextureRender.changeFragmentShader(mShader);
                    mShader = null;
                }
            }

            mTextureRender.drawFrame(mSurfaceTexture);
        }

        public void onSurfaceChanged(GL10 glUnused, int width, int height) {

        }

        public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
            mTextureRender.surfaceCreated();

            /*
             * Create the SurfaceTexture that will feed this textureID,
             * and pass it to the MediaPlayer
             */
            mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
            mSurfaceTexture.setOnFrameAvailableListener(this);

            synchronized(this) {
                updateSurface = false;
            }
        }

        synchronized public void onFrameAvailable(SurfaceTexture surface) {
            updateSurface = true;
        }

        public SurfaceTexture getSurfaceTexture() {
            return mSurfaceTexture;
        }

        public void changeEffect(String shader){
            synchronized(this) {
                if (shader != null) {
                    mShader = shader;
                }
            }
        }
    }  // End of class VideoRender.

    public SurfaceTexture getSurfaceTexture() {
        return mRenderer.getSurfaceTexture();
    }

    public void changeEffect(int effectType, int effectArgs) {
        mEffect = VideoEffect.createEffect(effectType, effectArgs);
        mRenderer.changeEffect(mEffect.getShader(this.getWidth(), this.getHeight()));
    }
}  // End of class VideoSurfaceView.
