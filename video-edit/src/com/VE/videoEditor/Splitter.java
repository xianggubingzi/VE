package com.VE.videoEditor;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaMuxer.OutputFormat;
import android.util.Log;

class Splitter {
    private MediaExtractor extractor;
    private MediaMuxer muxer;

    private String srcFile;
    private String dstFile;

    private long startTime;
    private long stopTime;

    private boolean mQuit = false;
    private MediaFormat mVideoFormat = null;
    private MediaFormat mAudioFormat = null;

    private String TAG = "splitter";

    // 设置需要裁剪的文件
    public void setSrcFile(String file) {
        this.srcFile = file;
    }

    // 设置生成的文件名.
    public void setDstFile(String file) {
        this.dstFile = file;
    }

    // 传进来的是单位是秒,需要经过一次转换.转为US
    public void setSplit(long start, long stop) {
        this.startTime = start;
        this.stopTime = stop;
    }

    public void forceQuit() {
        this.mQuit = true;
    }

    // 开始裁剪
    public int process() throws IOException {
        // 1.从源文件取出音频和视频的信息.
        if (srcFile == null || dstFile == null) {
            Log.e(TAG, "didn't set input or output file, mSrcFils is" + srcFile
                    + "mDstFile is" + dstFile);
            return Editor.ERROR_FILE;
        }
        extractor = new MediaExtractor();
        extractor.setDataSource(srcFile);

        MediaFormat formatTemp;

        int trackVideo = 0;
        int trackAudio = 0;

        int width = 0;
        int height = 0;

        int maxCapacity = 32 * 1024; // 最大帧尺寸.用来接收从文件里读取的帧数据.

        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; ++i) {
            formatTemp = extractor.getTrackFormat(i);
            String strMime = formatTemp.getString(MediaFormat.KEY_MIME);
            if (strMime.startsWith("video")) {
                mVideoFormat = formatTemp;
                trackVideo = i;
                width = formatTemp.getInteger(MediaFormat.KEY_WIDTH);
                height = formatTemp.getInteger(MediaFormat.KEY_HEIGHT);

                maxCapacity = width * height;
                extractor.selectTrack(i);
            } else if (strMime.startsWith("audio/mp4a-latm")) {
                mAudioFormat = formatTemp;
                trackAudio = i;
                extractor.selectTrack(i);
            }
        }

        // 2.创建跟源文件同样格式的目的文件.
        muxer = new MediaMuxer(dstFile, OutputFormat.MUXER_OUTPUT_MPEG_4);

        // 没有可用的track.
        if (mVideoFormat == null && mAudioFormat == null) {
            return Editor.ERROR_FILE;
        }

        // 源文件的视频和音频需要一个影射关系到目的文件.
        int videoTrackIndex = 0;
        int audioTrackIndex = 0;

        if (mVideoFormat != null) {
            videoTrackIndex = muxer.addTrack(mVideoFormat);
        }

        if (mAudioFormat != null) {
            audioTrackIndex = muxer.addTrack(mAudioFormat);
        }

        setParameterFromFormat();
        muxer.start();

        // extractor.advance();
        // 3.开始从源文件取数据加到目的文件.
        extractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        BufferInfo info = new BufferInfo();
        ByteBuffer inputBuffer = ByteBuffer.allocate(maxCapacity);

        do {
            info.size = extractor.readSampleData(inputBuffer, 0);
            if (info.size < 0) {
                break;
            }

            int trackIndex = extractor.getSampleTrackIndex();
            long presentationTimeUs = extractor.getSampleTime();

            // 大于结束时间.退出.
            if (stopTime != -1 && presentationTimeUs > stopTime) {
                break;
            }

            // 不是我们想要的track.
            if (trackIndex != trackVideo && trackIndex != trackAudio) {
                extractor.advance();
                continue;
            }

            info.offset = 0;
            info.flags = extractor.getSampleFlags();
            info.presentationTimeUs = presentationTimeUs;

            int currentTrackIndex = (trackIndex == trackAudio) ? audioTrackIndex
                    : videoTrackIndex;
            muxer.writeSampleData(currentTrackIndex, inputBuffer, info);

            extractor.advance();
        } while (!mQuit);

        // 结束,清除所有变量
        muxer.stop();
        extractor.release();
        muxer.release();

        extractor = null;
        muxer = null;

        return Editor.ERROR_NONE;
    }

    // 额外的一些参数配置，比如角度等
    private void setParameterFromFormat() {
        try {
            int rotationDegrees = mVideoFormat.getInteger("rotation-degrees");
            Log.i(TAG, "rotationDegrees is" + rotationDegrees);
            muxer.setOrientationHint(rotationDegrees);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
}