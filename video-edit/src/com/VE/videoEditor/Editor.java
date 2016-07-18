package com.VE.videoEditor;

import java.io.IOException;
import java.util.ArrayList;

import com.VE.videoEditor.Effect.interfaces.VideoEffect;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

/**
 * Editor类提供视频编辑的主要功能
 */
public class Editor {

    /**
     * 返回错误码：无错误
     */
    public static final int ERROR_NONE = 0;

    /**
     * 返回错误码：未知错误
     */
    public static final int ERROR_UNKONW = -1;

    /**
     * 返回错误码：IO错误
     */
    public static final int ERROR_IO = -2;

    /**
     * 返回错误码：文件错误，未设置文件等等。
     */
    public static final int ERROR_FILE = -3;

    /**
     * 返回错误码：并没有任何改变，不用编辑
     */
    public static final int ERROR_NO_CHANGE = -4;

    /**
     * 返回错误码：输出分辨率不支持
     */
    public static final int ERROR_OUT_SIZE = -5;

    /**
     * 返回错误码：解码错误
     */
    public static final int ERROR_DECODE = -6;

    /**
     * 返回错误码：编码错误
     */
    public static final int ERROR_ENCODE = -7;

    /**
     * 返回错误码：滤镜生成时图形错误
     */
    public static final int ERROR_GRAPHIC = -8;

    /**
     * 返回错误码：没有初始化
     */
    public static final int ERROR_NO_INIT = -9;

    /**
     * 返回错误码：不支持的特效（滤镜）
     */
    public static final int ERROR_UNSPPORT_EFFECT = -10;

    private String mSrcFile, mDstFile;

    private long mBeginTime = 0;
    private long mEndTime = -1;
    private boolean mHasSplitTime = false;

    private int mEffectType;
    private int mEffectArgs = 100;

    private int mOutPutWidth;
    private int mOutPutHeight;
    private int mOutPutFrameRate;
    private int mOutPutBitRate;

    private int mInPutWidth;
    private int mInPutHeight;
    private int mInPutFrameRate = 30;
    private int mInPutBitRate;
    private long mInPutDuration;// us

    private MediaMetadataRetriever mRetriever;

    private float mScale = 1.0f;

    private Splitter mSplitter;
    private ReEncoder mEncoder;

    private boolean mHasEffect = false;
    private boolean mHasScale = false;
    private boolean mHasInited = false;

    private static final String MIMETYPE264 = "video/avc";
    private static final String TAG = "meizuEditor";

    /**
     * 初始化Editor，设置输入文件路径，输出文件路径 并且获取输入文件参数 在新建一个Editor对象之后必须调用
     * 
     * @param srcFile
     *            输入文件路径
     * @param dtsFile
     *            输出文件路径
     * @return ERROR_NONE 表示初始化成功. 其它表示失败
     */
    public int init(String srcFile, String dtsFile) {
        if (srcFile == null || dtsFile == null) {
            Log.e(TAG, "do not set srcFile or dtsFile, srcFile:" + srcFile
                    + "dstFile:" + dtsFile);
            return ERROR_FILE;
        }
        setSrcFile(srcFile);
        setDstFile(dtsFile);
        mThumbnail = new ArrayList<Bitmap>(DEFAULT_THUMBNAIL_COUNT);
        try {
            getInputParm();
        } catch (IOException e) {
            e.printStackTrace();
            return ERROR_IO;
        }
        mHasInited = true;
        return ERROR_NONE;
    }
    
    /**
     * 视频宽key值
     */
    public static final int KEY_VIDEO_WIDTH  = 1;
    /**
     * 视频高key值
     */
    public static final int KEY_VIDEO_HEIGHT = 2;
    /**
     * 视频时长key值
     */
    public static final int KEY_DURATION     = 3;

    /**
     * 取得视频的输入参数
     * 
     * @param key
     *          根据不同的key获取不同的参数。
     * @return < 0 表示失败, 
     */
    public int getInputVideoParm(int key){
        if (!mHasInited) return ERROR_NO_INIT;
        switch (key) {
        case KEY_VIDEO_WIDTH:
            return mInPutWidth;
        case KEY_VIDEO_HEIGHT:
            return mInPutHeight;
        case KEY_DURATION:
            return (int)(mInPutDuration / 1E6);
        default:
            return ERROR_UNKONW;
        }
    }

    /**
     * 设置Editor分辨率缩放属性 在process之前调用
     * 
     * @param scale
     *            缩放参数，1.0f表示不变，最大为1.0
     */
    public void setScale(float scale) {
        this.mScale = scale;
        if (scale != 1.0f)
            mHasScale = true;
        Log.i(TAG, "setScale : scale(" + scale + ")");
    }

    /**
     * 设置Editor裁剪开始和结束时间 在process之前调用
     * 
     * @param begin
     *            开始时间，单位为s
     * @param end
     *            结束时间，单位为s
     */
    public void setSplitTime(int begin, int end) {
        if (begin > 0)
            mBeginTime = (long) begin * 1000 * 1000; // ms, us
        if (end > 0)
            mEndTime = (long) end * 1000 * 1000;

        if (mBeginTime > mInPutDuration)
            mBeginTime = 0;
        if (mEndTime > mInPutDuration || mEndTime == -1)
            mEndTime = mInPutDuration;

        if (mBeginTime != 0 || (mEndTime != -1 && mEndTime != mInPutDuration)) {
            mHasSplitTime = true;
        }

        Log.i(TAG, "split begin time is " + mBeginTime / 1000
                + " ms, end time is " + mEndTime / 1000 + " ms");
    }

    private static final int DEFAULT_THUMBNAIL_COUNT = 10;
    private int mThumbnailCount = DEFAULT_THUMBNAIL_COUNT;
    private ArrayList<Bitmap> mThumbnail;

    /**
     * 设置Editor缩略图个数
     * 
     * @param Num
     *            缩略图个数
     */
    public void setThumbnailCount(int Num) {
        if (Num > 0 && mThumbnailCount != Num) {
            mThumbnailCount = Num;
            cleanThumbnail();
        }
    }

    /**
     * 获取缩略图列表
     * 
     * @return 缩略图列表，根据视频时长和缩略图个数，平均时长下获取的缩略图, 可能比较耗时。
     */
    public ArrayList<Bitmap> getThumbnail() {
        if (mThumbnail.size() == 0)
            createThumbnail();
        return mThumbnail;
    }

    private void createThumbnail() {
        long timeUs = 0;
        for (int i = 0; i < mThumbnailCount; i++) {
            timeUs = mInPutDuration * i / mThumbnailCount;
            Bitmap thumbnail = mRetriever.getFrameAtTime(timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST);
            mThumbnail.add(thumbnail);
        }
    }

    private void cleanThumbnail() {
        mThumbnail.clear();
    }

    private boolean hasSplitTime() {
        return mHasSplitTime;
    }

    private void setOutPutParm(int width, int height, int framerate, int bitrate) {
        mOutPutWidth = width;
        mOutPutHeight = height;
        mOutPutFrameRate = framerate;
        mOutPutBitRate = bitrate;
        Log.i(TAG, "output parameter: width(" + width + "), height(" + height
                + "), bitrate(" + bitrate / 1000 + " kbps), duration("
                + (mEndTime - mBeginTime) / 1000 + " ms)");
    }

    /**
     * 特效类型：无特效
     */
    public static final int EFFECT_NONE = VideoEffect.EFFECT_NONE;

    /**
     * Attempts to auto-fix the video based on histogram equalization.
     * 
     * <br>
     * <br>
     * argument:<br>
     * between 0 and 100. Zero means no adjustment, while 100 indicates the
     * maximum amount of adjustment.
     */
    public static final int EFFECT_AUTO_FIX = VideoEffect.EFFECT_AUTO_FIX;

    /**
     * Converts the video into black and white colors
     */
    public static final int EFFECT_BLACK_WHITE = VideoEffect.EFFECT_BLACK_WHITE;

    /**
     * Adjusts the brightness of the video.
     * 
     * <br>
     * <br>
     * argument:<br>
     * Range should be between 0～100 with 50 being normal.
     */
    public static final int EFFECT_BRIGHTNESS = VideoEffect.EFFECT_BRIGHTNESS;

    /**
     * Adjusts the contrast of the video
     * 
     * <br>
     * <br>
     * argument:<br>
     * Range should be between 0～100 with 50 being normal.
     */
    public static final int EFFECT_CONTRAST = VideoEffect.EFFECT_CONTRAST;

    /**
     * Applies a cross process effect on video, in which the red and green
     * channels are enhanced while the blue channel is restricted.
     */
    public static final int EFFECT_CROSS_PROCESS = VideoEffect.EFFECT_CROSS_PROCESS;

    /**
     * Applies black and white documentary style effect on video.
     */
    public static final int EFFECT_DOCUMENTARY = VideoEffect.EFFECT_DOCUMENTARY;

    /**
     * Representation of video using only two color tones.
     */
    public static final int EFFECT_DUOTONE = VideoEffect.EFFECT_DUOTONE;

    /**
     * Applies back-light filling to the video.
     * 
     * <br>
     * <br>
     * argument:<br>
     * between 0 and 100. 0 means no change.
     */
    public static final int EFFECT_FILL_LIGHT = VideoEffect.EFFECT_FILL_LIGHT;

    /**
     * Applies film grain effect to video
     * 
     * <br>
     * <br>
     * argument:<br>
     * between 0 and 100. 0 means no distortion, while 100 indicates the maximum
     * amount of adjustment.
     */
    public static final int EFFECT_GRAIN = VideoEffect.EFFECT_GRAIN;

    /**
     * Converts video to GreyScale.
     */
    public static final int EFFECT_GREY_SCALE = VideoEffect.EFFECT_GREY_SCALE;

    /**
     * Inverts the video colors. This can also be known as negative Effect.
     */
    public static final int EFFECT_INVERT_COLOR = VideoEffect.EFFECT_INVERT_COLOR;

    /**
     * Applies lomo-camera style effect to video.
     */
    public static final int EFFECT_LAMOISH = VideoEffect.EFFECT_LAMOISH;

    /**
     * Applies Posterization effect to video.
     */
    public static final int EFFECT_POSTERIZE = VideoEffect.EFFECT_POSTERIZE;

    /**
     * Adjusts color saturation of video. There is still some issue with this
     * effect.
     * 
     * <br>
     * <br>
     * argument:<br>
     * between 0 and 100. 50 means no change, while 0 indicates full
     * desaturation, i.e. grayscale.
     */
    public static final int EFFECT_SATURATION = VideoEffect.EFFECT_SATURATION;

    /**
     * Converts video to Sepia tone.
     */
    public static final int EFFECT_SEPIA = VideoEffect.EFFECT_SEPIA;

    /**
     * Sharpens the video.
     * 
     * <br>
     * <br>
     * argument:<br>
     * between 0 and 100. 0 means no change.
     */
    public static final int EFFECT_SHARPNESS = VideoEffect.EFFECT_SHARPNESS;

    /**
     * Adjusts color temperature of the video
     * 
     * <br>
     * <br>
     * argument:<br>
     * between 0 and 100, with 0 indicating cool, and 100 indicating warm. A
     * value of of 50 indicates no change.
     */
    public static final int EFFECT_TEMPERATURE = VideoEffect.EFFECT_TEMPERATURE;

    /**
     * Tints the video with specified color..
     */
    public static final int EFFECT_TINT = VideoEffect.EFFECT_TINT;

    /**
     * Applies lomo-camera style effect to video.
     * 
     * <br>
     * <br>
     * argument:<br>
     * between 0 and 100. 0 means no change.
     */
    public static final int EFFECT_VIGNETTE = VideoEffect.EFFECT_VIGNETTE;

    /**
     * Max number of effect.
     */
    public static final int EFFECT_MAX_NO = VideoEffect.EFFECT_MAX;

    /**
     * 设置Editor滤镜效果 在process之前调用
     * 
     * @param effectType
     *            滤镜类型
     * @param effectArgs
     *            额外的参数
     */
    public void setEffect(int effectType, int effectArgs) {
        this.mEffectType = effectType;
        this.mEffectArgs = effectArgs;
        if (effectType != VideoEffect.EFFECT_NONE)
            mHasEffect = true;
        Log.i(TAG,
                "setEffect : effectType("
                        + VideoEffect.getEffectName(effectType) + ")");
    }

    /**
     * 获取滤镜中文名称，该方法为static类型
     * 
     * @param effectType
     *            滤镜类型
     */
    public static String getEffectCnName(int effectType) {
        return VideoEffect.getEffectCnName(effectType);
    }

    /**
     * 强行停止Editor的process运行 需要与process运行在不同线程
     */
    public void forceEndProcess() {
        if (mSplitter != null) {
            mSplitter.forceQuit();
        }
        if (mEncoder != null) {
            mEncoder.forceQuit();
        }
    }

    /**
     * 进行视频编辑生成新的视频文件 在process运行之前，必须设置以下三点中一点：<br>
     * 1. 滤镜效果<br>
     * 2. 缩放大小<br>
     * 3. 裁剪时间
     */
    public int process() {
        if (!mHasInited) {
            Log.e(TAG, "has not init");
            return ERROR_NO_INIT;
        }

        int res = changeOutPutParmAndCheck();
        if (res != ERROR_NONE) {
            return res;
        }

        try {
            if (needReEncode())
                return processReEncode();
            else
                return processSplit();
        } catch (IOException e) {
            e.printStackTrace();
            return ERROR_IO;
        }
    }

    /**
     * 析构方法，必须调用，否则可能造成内存泄露。
     */
    public void release() {
        if (mRetriever != null) {
            mRetriever.release();
        }
    }

    private boolean needReEncode() {
        return mHasScale || mHasEffect;
    }

    private void getInputParm() throws IOException {
        mRetriever = new MediaMetadataRetriever();
        mRetriever.setDataSource(mSrcFile);

        mInPutHeight = Integer
                .parseInt(mRetriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        mInPutWidth = Integer
                .parseInt(mRetriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        mInPutBitRate = Integer.parseInt(mRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        String dur = mRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        mInPutDuration = (long) Integer.parseInt(dur) * 1000;

        Log.i(TAG, "input parameter: width(" + mInPutWidth + "), height("
                + mInPutHeight + "), bitrate(" + mInPutBitRate / 1000
                + " kbps), duration(" + mInPutDuration / 1000 + " ms)");
        Log.i(TAG, "create Thumbnail end");
    }

    private int changeOutPutParmAndCheck() {
        if (needReEncode()) {
            int width = (int) (mScale * mInPutWidth);
            int height = (int) (mScale * mInPutHeight);
            int bitrate = (int) (mScale * mScale * mInPutBitRate);
            width = (width >> 4) << 4; // 16对齐
            height = (height >> 4) << 4; // 16对齐
            setOutPutParm(width, height, mInPutFrameRate, bitrate);
            if (width > 1920 || height > 1080) {
                Log.e(TAG, "output size is width:" + width + " height:"
                        + height + ". unsupport encoder size");
                return ERROR_OUT_SIZE;
            }
            if (mEffectType > EFFECT_MAX_NO) {
                Log.e(TAG, "output mEffectType is :" + mEffectType
                        + ". unsupport mEffectType");
                return ERROR_UNSPPORT_EFFECT;
            }
        } else if (!hasSplitTime()) {
            Log.e(TAG, "video don't need edit");
            return ERROR_NO_CHANGE;
        }
        return ERROR_NONE;
    }

    private int processReEncode() throws IOException {
        mEncoder = new ReEncoder();
        mEncoder.setSrcFile(mSrcFile);
        mEncoder.setDstFile(mDstFile);
        if (mEndTime != -1)
            mEncoder.setSplit(mBeginTime, mEndTime);
        mEncoder.setEffect(VideoEffect.createEffect(mEffectType, mEffectArgs));
        return mEncoder.process(mOutPutWidth, mOutPutHeight, mOutPutFrameRate,
                mOutPutBitRate, false, MIMETYPE264);
    }

    private int processSplit() throws IOException {
        mSplitter = new Splitter();
        mSplitter.setSrcFile(mSrcFile);
        mSplitter.setDstFile(mDstFile);
        mSplitter.setSplit(mBeginTime, mEndTime);
        return mSplitter.process();
    }

    private void setSrcFile(String file) {
        mSrcFile = file;
    }

    // 设置生成的文件名.
    private void setDstFile(String file) {
        mDstFile = file;
    }
}
