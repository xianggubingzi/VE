package com.VE.videoEditor;

/*
 * Copyright 2014 The Android Open Source Project
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
//import android.media.cts.CodecUtils;
import android.graphics.SurfaceTexture;
//import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaMuxer.OutputFormat;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;

import com.VE.videoEditor.Effect.interfaces.ShaderInterface;

public class ReEncoder {
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private static final String TAG = "videoProcessEditor";
    private static final long FRAME_TIMEOUT_MS = 1000;
    // use larger delay before we get first frame, some encoders may need more
    // time
    private static final long INIT_TIMEOUT_MS = 2000;

    private String mSrcFile;
    private String mDstFile;
    private long mStartTime = 0;
    private long mStopTime = -1;
    private ShaderInterface mEffect;

    private final boolean DEBUG = true;
    private VideoProcessorBase mProcessor;

    public void setSrcFile(String file) {
        this.mSrcFile = file;
    }

    public void forceQuit() {
        if (mProcessor != null) {
            mProcessor.forceQuit();
        }
    }

    // 设置生成的文件名.
    public void setDstFile(String file) {
        this.mDstFile = file;
    }

    // 传进来的是单位是秒,需要经过一次转换.转为US
    public void setSplit(long start, long stop) {
        this.mStartTime = start;
        this.mStopTime = stop;
    }

    public void setEffect(ShaderInterface effect) {
        this.mEffect = effect;
    }

    class VideoStorage {
        private LinkedList<Pair<ByteBuffer, BufferInfo>> mStream;
        private MediaFormat mFormat;
        private int mInputBufferSize;

        public VideoStorage() {
            mStream = new LinkedList<Pair<ByteBuffer, BufferInfo>>();
        }

        public void setFormat(MediaFormat format) {
            mFormat = format;
        }

        public void addBuffer(ByteBuffer buffer, BufferInfo info) {
            ByteBuffer savedBuffer = ByteBuffer.allocate(info.size);
            savedBuffer.put(buffer);
            if (info.size > mInputBufferSize) {
                mInputBufferSize = info.size;
            }
            BufferInfo savedInfo = new BufferInfo();
            savedInfo.set(0, savedBuffer.position(), info.presentationTimeUs,
                    info.flags);
            mStream.addLast(Pair.create(savedBuffer, savedInfo));
        }

        private void play(MediaCodec decoder, Surface surface) {
            decoder.reset();
            final Object condition = new Object();
            final Iterator<Pair<ByteBuffer, BufferInfo>> it = mStream
                    .iterator();
            decoder.setCallback(new MediaCodec.Callback() {
                public void onOutputBufferAvailable(MediaCodec codec, int ix,
                        BufferInfo info) {
                    codec.releaseOutputBuffer(ix, info.size > 0);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        synchronized (condition) {
                            condition.notifyAll();
                        }
                    }
                }

                public void onInputBufferAvailable(MediaCodec codec, int ix) {
                    if (it.hasNext()) {
                        Pair<ByteBuffer, BufferInfo> el = it.next();
                        el.first.clear();
                        try {
                            codec.getInputBuffer(ix).put(el.first);
                        } catch (java.nio.BufferOverflowException e) {
                            Log.e(TAG,
                                    "cannot fit "
                                            + el.first.limit()
                                            + "-byte encoded buffer into "
                                            + codec.getInputBuffer(ix)
                                                    .remaining()
                                            + "-byte input buffer of "
                                            + codec.getName()
                                            + " configured for "
                                            + codec.getInputFormat());
                            throw e;
                        }
                        BufferInfo info = el.second;
                        codec.queueInputBuffer(ix, 0, info.size,
                                info.presentationTimeUs, info.flags);
                    }
                }

                public void onError(MediaCodec codec,
                        MediaCodec.CodecException e) {
                    Log.i(TAG, "got codec exception", e);
                    Log.e(TAG, "received codec error during decode" + e);
                }

                public void onOutputFormatChanged(MediaCodec codec,
                        MediaFormat format) {
                    Log.i(TAG, "got output format " + format);
                }
            });
            mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mInputBufferSize);
            decoder.configure(mFormat, surface, null /* crypto */, 0 /* flags */);
            decoder.start();
            synchronized (condition) {
                try {
                    condition.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "playback interrupted");
                }
            }
            decoder.stop();
        }

        public void playAll(Surface surface) {
            if (mFormat == null) {
                Log.i(TAG, "no stream to play");
                return;
            }
            String mime = mFormat.getString(MediaFormat.KEY_MIME);
            MediaCodecList mcl = new MediaCodecList(
                    MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info : mcl.getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                MediaCodec codec = null;
                try {
                    CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (!caps.isFormatSupported(mFormat)) {
                        continue;
                    }
                    codec = MediaCodec.createByCodecName(info.getName());
                } catch (IllegalArgumentException | IOException e) {
                    continue;
                }
                play(codec, surface);
                codec.release();
            }
        }
    }

    class AudioWritter extends Thread {
        private MediaMuxer mMuxer;
        private boolean mIsMuxerStared = false;
        private MediaFormat mMediaFormat;
        private LinkedList<Pair<ByteBuffer, BufferInfo>> mStream;
        private int mInputBufferSize;
        private final Object mCondition = new Object();
        private int mOutTrackIndex = -1;
        private boolean mQuit = false;
        private boolean mListEmpty = true;
        private boolean mIsEos = false;

        @Override
        public void run() {
            boolean hasDataToWrite = false;
            while (!mQuit) {
                synchronized (mCondition) {
                    mListEmpty = mStream.isEmpty();
                    if (mListEmpty && mIsEos) {
                        break;
                    }
                    try {
                        if (!mIsMuxerStared || mListEmpty) {
                            mCondition.wait(100);
                        }
                    } catch (InterruptedException ie) {
                        Log.e(TAG, "wait interrupted"); // shouldn't happen
                    }
                    hasDataToWrite = mIsMuxerStared && !mListEmpty;
                }
                if (hasDataToWrite)
                    writeData();
            }
            synchronized (mCondition) {
                while (!mStream.isEmpty()) {
                    Pair<ByteBuffer, BufferInfo> el = mStream.removeFirst();
                    el.first.clear();
                }
            }
        }

        public AudioWritter(MediaMuxer muxer) {
            this.mStream = new LinkedList<Pair<ByteBuffer, BufferInfo>>();
            this.mMuxer = muxer;
            this.start();
        }

        public void forceEndWrite() {
            this.mQuit = true;
        }

        public void setEos(boolean isEos) {
            this.mIsEos = isEos;
        }

        public void setMuxerStart(boolean started) {
            this.mIsMuxerStared = started;
        }

        private void addBuffer(ByteBuffer buffer, BufferInfo info) {
            ByteBuffer savedBuffer = ByteBuffer.allocate(info.size);
            savedBuffer.put(buffer);
            if (info.size > mInputBufferSize) {
                mInputBufferSize = info.size;
            }
            BufferInfo savedInfo = new BufferInfo();
            savedInfo.set(0, savedBuffer.position(), info.presentationTimeUs,
                    info.flags);
            synchronized (mCondition) {
                mStream.addLast(Pair.create(savedBuffer, savedInfo));
            }
        }

        public void writeToMuxerAsync(ByteBuffer buffer, BufferInfo info) {
            addBuffer(buffer, info);
            if (mIsMuxerStared) {
                synchronized (mCondition) {
                    mCondition.notify();
                }
            }
        }

        public boolean setMediaFormat(MediaFormat format) {
            this.mMediaFormat = format;
            if (mIsMuxerStared)
                return false;
            mOutTrackIndex = mMuxer.addTrack(mMediaFormat);
            return true;
        }

        private void writeData() {
            Pair<ByteBuffer, BufferInfo> el;
            synchronized (mCondition) {
                el = mStream.removeFirst();
            }
            if (DEBUG)
                Log.v(TAG, "writeSampleData, mOutTrackIndex:" + mOutTrackIndex);
            if (el != null)
                mMuxer.writeSampleData(mOutTrackIndex, el.first, el.second);
            el.first.clear();
        }
    }

    abstract class VideoProcessorBase extends MediaCodec.Callback {
        private static final String TAG = "VideoProcessorBase";

        private MediaExtractor mExtractor;
        private MediaMuxer mMuxer;
        private AudioWritter mAudioWritter;
        private boolean mMuxerStartd = false;
        private ByteBuffer mBuffer;
        private int mVideoTrackIndex = -1;
        private int mAudioTrackIndex = -1;
        private int mOutTrackIndex = -1;
        private boolean mSignaledDecoderEOS;

        protected int mWidth;
        protected int mHeight;

        protected boolean mCompleted;
        protected boolean mEncodeOutputFormatUpdated;
        protected final Object mCondition = new Object();

        protected MediaFormat mVideoFormat;
        protected MediaFormat mAudioFormat;
        protected int mRotation = 0;
        protected MediaCodec mDecoder, mEncoder;

        // private VideoStorage mEncodedStream;
        protected int mFrameRate = 0;
        protected int mBitRate = 0;

        private long mBeginTime = 0;

        private long mEndTime = -1;

        protected void open(String path) throws IOException {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(path);

            mMuxer = new MediaMuxer(mDstFile, OutputFormat.MUXER_OUTPUT_MPEG_4);

            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat fmt = mExtractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME).toLowerCase();
                if (mime.startsWith("video/")) {
                    mVideoTrackIndex = i;
                    mVideoFormat = fmt;
                    mWidth = mVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
                    mHeight = mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    int maxCapacity = mWidth * mHeight;
                    if (maxCapacity != 0) {
                        mBuffer = ByteBuffer.allocate(maxCapacity);
                    } else {
                        mBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
                    }
                    mExtractor.selectTrack(i);
                    // break;
                } else if (mime.startsWith("audio/mp4a-latm")) {
                    mAudioFormat = fmt;
                    mAudioTrackIndex = i;
                    mExtractor.selectTrack(i);
                }
            }

            if (mAudioTrackIndex != -1) {
                mAudioWritter = new AudioWritter(mMuxer);
                mAudioWritter.setMuxerStart(false);
            }
            if (mBeginTime != 0) {
                mExtractor.seekTo(mBeginTime,
                        MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            }

            Log.i(TAG, "open path sucess: " + path);
            // mEncodedStream = new VideoStorage();
        }

        // returns true if encoder supports the size
        protected boolean initCodecsAndConfigureEncoder(String outMime,
                int width, int height, int colorFormat) throws IOException {
            mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

            mDecoder = MediaCodec.createDecoderByType(mVideoFormat
                    .getString(MediaFormat.KEY_MIME));
            mEncoder = MediaCodec.createEncoderByType(outMime);

            if (mDecoder == null || mEncoder == null) {
                Log.e(TAG, "mDecoder is" + mDecoder + "mEncoder is" + mEncoder);
                return false;
            }
            Log.i(TAG, "decode  format: " + mVideoFormat);

            mDecoder.setCallback(this);
            mEncoder.setCallback(this);

            VideoCapabilities encCaps = mEncoder.getCodecInfo()
                    .getCapabilitiesForType(outMime).getVideoCapabilities();
            if (!encCaps.isSizeSupported(width, height)) {
                Log.i(TAG, "does not support size: " + width + "x" + height);
                return false;
            }

            MediaFormat outFmt = MediaFormat.createVideoFormat(outMime, width,
                    height);

            {
                int maxWidth = encCaps.getSupportedWidths().getUpper();
                int maxHeight = encCaps.getSupportedHeightsFor(maxWidth)
                        .getUpper();
                int frameRate = mFrameRate;
                if (frameRate <= 0) {
                    int maxRate = encCaps
                            .getSupportedFrameRatesFor(maxWidth, maxHeight)
                            .getUpper().intValue();
                    frameRate = Math.min(30, maxRate);
                }
                outFmt.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);

                int bitRate = mBitRate;
                if (bitRate <= 0) {
                    bitRate = encCaps.getBitrateRange().clamp(
                            (int) (encCaps.getBitrateRange().getUpper() / Math
                                    .sqrt((double) maxWidth * maxHeight / width
                                            / height)));
                }
                outFmt.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);

                Log.d(TAG, "frame rate = " + frameRate + ", bit rate = "
                        + bitRate);
            }
            outFmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            outFmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            mEncoder.configure(outFmt, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.i(TAG, "encoder input format " + mEncoder.getInputFormat()
                    + " from " + outFmt);
            return true;
        }

        protected void close() {
            if (mDecoder != null) {
                mDecoder.release();
                mDecoder = null;
            }
            if (mEncoder != null) {
                mEncoder.release();
                mEncoder = null;
            }
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
            if (mAudioWritter != null) {
                try {
                    mAudioWritter.setEos(true);
                    mAudioWritter.join();// 等待退出。
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (mMuxer != null) {
                if (mMuxerStartd) {
                    mMuxer.stop();
                    mMuxerStartd = false;
                }
                mMuxer.release();
                mMuxer = null;
            }
        }

        public void forceQuit() {
            mCompleted = true;
            if (mAudioWritter != null) {
                mAudioWritter.forceEndWrite();
            }
            close();
        }

        // returns true if filled buffer
        protected boolean fillDecoderInputBuffer(int ix) {
            if (DEBUG)
                Log.v(TAG, "decoder received input #" + ix);
            while (!mSignaledDecoderEOS) {
                int track = mExtractor.getSampleTrackIndex();
                if (track >= 0
                        && (track != mVideoTrackIndex && track != mAudioTrackIndex)) {
                    mExtractor.advance();
                    continue;
                }
                int size = mExtractor.readSampleData(mBuffer, 0);
                long presentationTimeUs = mExtractor.getSampleTime();
                if (size < 0
                        || (mEndTime != -1 && presentationTimeUs > mEndTime)) {
                    // queue decoder input EOS
                    if (DEBUG)
                        Log.v(TAG, "queuing decoder EOS");
                    mDecoder.queueInputBuffer(ix, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                    if (mAudioWritter != null)
                        mAudioWritter.setEos(true);

                    mSignaledDecoderEOS = true;
                } else {
                    mBuffer.limit(size);
                    mBuffer.position(0);
                    BufferInfo info = new BufferInfo();
                    info.set(0, mBuffer.limit(), mExtractor.getSampleTime(),
                            mExtractor.getSampleFlags());
                    if (track == mVideoTrackIndex) {
                        mDecoder.getInputBuffer(ix).put(mBuffer);
                        if (DEBUG)
                            Log.v(TAG, "queing input #" + ix
                                    + " for decoder with timestamp "
                                    + info.presentationTimeUs);
                        mDecoder.queueInputBuffer(ix, 0, mBuffer.limit(),
                                info.presentationTimeUs, 0);
                    } else {
                        if (mAudioWritter != null)
                            mAudioWritter.writeToMuxerAsync(mBuffer, info);

                        mExtractor.advance();
                        continue;
                    }
                }
                mExtractor.advance();
                return true;
            }
            return false;
        }

        protected void emptyEncoderOutputBuffer(int ix, BufferInfo info) {
            if (DEBUG)
                Log.v(TAG, "encoder received output #" + ix + " (sz="
                        + info.size + ", f=" + info.flags + ", ts="
                        + info.presentationTimeUs + ")");
            if (mMuxer != null && mOutTrackIndex != -1) {
                if (DEBUG)
                    Log.v(TAG, "enter writeSampleData, mOutTrackIndex is "
                            + mOutTrackIndex);
                mMuxer.writeSampleData(mOutTrackIndex,
                        mEncoder.getOutputBuffer(ix), info);
            }
            // mEncodedStream.addBuffer(mEncoder.getOutputBuffer(ix), info);
            if (!mCompleted) {
                mEncoder.releaseOutputBuffer(ix, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "encoder received output EOS");
                    synchronized (mCondition) {
                        mCompleted = true;
                        mCondition.notifyAll(); // condition is always satisfied
                    }
                }
            }
        }

        protected void saveEncoderFormat(MediaFormat format) {
            // mEncodedStream.setFormat(format);
        }

        protected void setFormatAndStartMuxer(MediaFormat format) {
            if (mMuxer != null && !mMuxerStartd) {
                // mOutTrackIndex is VideoTrack
                mOutTrackIndex = mMuxer.addTrack(format);
                Log.i(TAG, "rotationDegrees is" + mRotation);
                mMuxer.setOrientationHint(mRotation);

                if (mAudioWritter != null) {
                    mAudioWritter.setMediaFormat(mAudioFormat);
                    mAudioWritter.setMuxerStart(true);
                }

                mMuxerStartd = true;
                mMuxer.start();
            }
        }

        public void setFrameAndBitRates(int frameRate, int bitRate) {
            mFrameRate = frameRate;
            mBitRate = bitRate;
        }

        public void setBeginEndTime(long begin, long end) {
            mBeginTime = begin;
            mEndTime = end;
        }

        public abstract int processLoop(String path, String outMime, int width,
                int height);
    };

    class SurfaceVideoProcessor extends VideoProcessorBase implements
            SurfaceTexture.OnFrameAvailableListener {
        private static final String TAG = "SurfaceVideoProcessor";
        private boolean mFrameAvailable;
        private boolean mEncoderIsActive;
        private boolean mGotDecoderEOS;
        private boolean mSignaledEncoderEOS;

        private InputSurface mEncSurface;
        private OutputSurface mDecSurface;
        private BufferInfo mInfoOnSurface;

        private ShaderInterface mEffect;

        private LinkedList<Pair<Integer, BufferInfo>> mBuffersToRender = new LinkedList<Pair<Integer, BufferInfo>>();

        public SurfaceVideoProcessor(ShaderInterface effect) {
            this.mEffect = effect;
        }

        @Override
        public int processLoop(String path, String outMime, int width,
                int height) {
            // boolean skipped = true;
            try {
                open(path);
                if (!initCodecsAndConfigureEncoder(outMime, width, height,
                        CodecCapabilities.COLOR_FormatSurface)) {
                    Log.e(TAG, "could not configure encoder for supported size");
                    return Editor.ERROR_ENCODE;
                }
                // skipped = false;

                mEncSurface = new InputSurface(mEncoder.createInputSurface());
                mEncSurface.makeCurrent();

                mDecSurface = new OutputSurface(this);
                // mDecSurface.changeFragmentShader(mEffect.getShader(width,
                // height));
                if (mEffect != null)
                    mDecSurface.changeFragmentShader(mEffect.getShader(mWidth,
                            mHeight));
                splitRotationFormat();
                mDecoder.configure(mVideoFormat, mDecSurface.getSurface(),
                        null /* crypto */, 0);

                mDecoder.start();
                mEncoder.start();

                // main loop - process GL ops as only main thread has GL context
                while (!mCompleted) {
                    BufferInfo info = null;
                    synchronized (mCondition) {
                        try {
                            // wait for mFrameAvailable, which is set by
                            // onFrameAvailable().
                            // Use a timeout to avoid stalling the test if it
                            // doesn't arrive.
                            if (!mFrameAvailable && !mCompleted
                                    && !mEncoderIsActive) {
                                mCondition
                                        .wait(mEncodeOutputFormatUpdated ? FRAME_TIMEOUT_MS
                                                : INIT_TIMEOUT_MS);
                            }
                        } catch (InterruptedException ie) {
                            Log.e(TAG, "wait interrupted"); // shouldn't happen
                        }
                        if (mCompleted) {
                            break;
                        }
                        if (mEncoderIsActive) {
                            mEncoderIsActive = false;
                            if (DEBUG)
                                Log.d(TAG, "encoder is still active, continue");
                            continue;
                        }
                        // assertTrue("still waiting for image",
                        // mFrameAvailable);
                        if (DEBUG)
                            Log.v(TAG, "got image");
                        info = mInfoOnSurface;
                    }
                    if (info == null) {
                        continue;
                    }
                    if (info.size > 0) {
                        mDecSurface.latchImage();
                        if (DEBUG)
                            Log.v(TAG, "latched image");
                        mFrameAvailable = false;

                        mDecSurface.drawImage();
                        if (DEBUG)
                            Log.d(TAG, "encoding frame at "
                                    + info.presentationTimeUs * 1000);

                        mEncSurface
                                .setPresentationTime(info.presentationTimeUs * 1000);
                        mEncSurface.swapBuffers();
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mSignaledEncoderEOS = true;
                        Log.d(TAG, "signaling encoder EOS");
                        mEncoder.signalEndOfInputStream();
                    }

                    synchronized (mCondition) {
                        mInfoOnSurface = null;
                        if (mBuffersToRender.size() > 0
                                && mInfoOnSurface == null) {
                            if (DEBUG)
                                Log.v(TAG, "handling postponed frame");
                            Pair<Integer, BufferInfo> nextBuffer = mBuffersToRender
                                    .removeFirst();
                            renderDecodedBuffer(nextBuffer.first,
                                    nextBuffer.second);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "received exception " + e);
            } finally {
                close();
                if (mEncSurface != null) {
                    mEncSurface.release();
                    mEncSurface = null;
                }
                if (mDecSurface != null) {
                    mDecSurface.release();
                    mDecSurface = null;
                }
            }
            return Editor.ERROR_NONE;
        }

        private void splitRotationFormat() {
            try {
                mRotation = mVideoFormat.getInteger("rotation-degrees");
                Log.i(TAG, "mRotation is" + mRotation);
                mVideoFormat.setInteger("rotation-degrees", 0);
            } catch (NullPointerException e) {
                // do nothing;
                Log.i(TAG, "don't have rotation");
            }
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            if (DEBUG)
                Log.v(TAG, "new frame available");
            synchronized (mCondition) {
                // assertFalse("mFrameAvailable already set, frame could be dropped",
                // mFrameAvailable);
                mFrameAvailable = true;
                mCondition.notifyAll();
            }
        }

        @Override
        public void onInputBufferAvailable(MediaCodec mediaCodec, int ix) {
            if (mediaCodec == mDecoder) {
                // fill input buffer from extractor
                fillDecoderInputBuffer(ix);
            } else {
                Log.e(TAG, "received input buffer on " + mediaCodec.getName());
            }
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int ix,
                BufferInfo info) {
            if (mediaCodec == mDecoder) {
                if (DEBUG)
                    Log.v(TAG, "decoder received output #" + ix + " (sz="
                            + info.size + ", f=" + info.flags + ", ts="
                            + info.presentationTimeUs + ")");
                // render output buffer from decoder
                if (!mGotDecoderEOS) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (eos) {
                        mGotDecoderEOS = true;
                    }
                    // can release empty buffers now
                    if (info.size == 0) {
                        mDecoder.releaseOutputBuffer(ix, false /* render */);
                        ix = -1; // dummy index used by render to not render
                    }
                    if (eos || info.size > 0) {
                        synchronized (mCondition) {
                            if (mInfoOnSurface != null
                                    || mBuffersToRender.size() > 0) {
                                if (DEBUG)
                                    Log.v(TAG,
                                            "postponing render, surface busy");
                                mBuffersToRender.addLast(Pair.create(ix, info));
                            } else {
                                renderDecodedBuffer(ix, info);
                            }
                        }
                    }
                }
            } else if (mediaCodec == mEncoder) {
                emptyEncoderOutputBuffer(ix, info);
                synchronized (mCondition) {
                    if (!mCompleted) {
                        mEncoderIsActive = true;
                        mCondition.notifyAll();
                    }
                }
            } else {
                Log.e(TAG, "received output buffer on " + mediaCodec.getName());
            }
        }

        private void renderDecodedBuffer(int ix, BufferInfo info) {
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            mInfoOnSurface = info;
            if (info.size > 0) {
                if (DEBUG)
                    Log.d(TAG, "rendering frame #" + ix + " at "
                            + info.presentationTimeUs * 1000
                            + (eos ? " with EOS" : ""));
                mDecoder.releaseOutputBuffer(ix, info.presentationTimeUs * 1000);
            }

            if (eos && info.size == 0) {
                if (DEBUG)
                    Log.v(TAG, "decoder output EOS available");
                mFrameAvailable = true;
                mCondition.notifyAll();
            }
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            Log.e(TAG, "received error on " + mediaCodec.getName() + ": " + e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec,
                MediaFormat mediaFormat) {
            Log.i(TAG, mediaCodec.getName() + " got new output format "
                    + mediaFormat);
            if (mediaCodec == mEncoder) {
                mEncodeOutputFormatUpdated = true;
                mediaFormat.setInteger("rotation-degrees", mRotation);
                setFormatAndStartMuxer(mediaFormat);
                // saveEncoderFormat(mediaFormat);
            }
        }
    }

    public int process(int width, int height, int frameRate, int bitRate,
            boolean flexYUV, String outMime) {
        if (mSrcFile == null || mDstFile == null) {
            Log.e(TAG, "didn't set input or output file, mSrcFils is"
                    + mSrcFile + "mDstFile is" + mDstFile);
            return Editor.ERROR_FILE;
        }

        Log.i(TAG, "process encode" + outMime + " for " + width + "x" + height);

        // VideoProcessorBase processor =
        // flexYUV ? new VideoProcessor() : new
        // SurfaceVideoProcessor(mEffectNo);
        mProcessor = new SurfaceVideoProcessor(mEffect);
        mProcessor.setFrameAndBitRates(frameRate, bitRate);
        mProcessor.setBeginEndTime(mStartTime, mStopTime);
        // We are using a resource URL as an example
        int res = mProcessor.processLoop(mSrcFile, outMime, width, height);
        return res;
    }
}
