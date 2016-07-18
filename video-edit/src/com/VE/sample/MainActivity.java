package com.VE.sample;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.VE.videoEditor.Editor;
import com.VE.videoEditor.view.VideoSurfaceView2;
import com.meizu.videoeditor.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String DEFAULT_PATH = "/sdcard/Movies/";

    private String mSrcFile = "/sdcard/Movies/test.mp4";
    private String mDtsFile = "/sdcard/Movies/result.mp4";

    private int mBeginTime = 0;
    private int mEndTime = -1;

    private EditText mInputName, mOutputName, mEtBeginTime, mEtEndTime;
    private Spinner mSpinnerScale;
    private ArrayAdapter mAdapterScale;
    private float mScale = 1.0f;

    private Spinner mSpinnerEffect;
    private ArrayAdapter mAdapterEffect;
    private int mEffectNo = 0;

    private VideoSurfaceView2 mVideoView = null;

    private ProgressDialog mQueryDialog = null;

    private Button btnStart;
    private Button btnStop;
    private Button btnpreview;

    private MediaPlayer mMediaPlayer;

    private LinearLayout mLayOut;

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        mMediaPlayer = new MediaPlayer();

        mInputName = (EditText) findViewById(R.id.et_input_name);
        mOutputName = (EditText) findViewById(R.id.et_output_name);
        mEtBeginTime = (EditText) findViewById(R.id.et_begin_time);
        mEtEndTime = (EditText) findViewById(R.id.et_end_time);
        // mInputName.getText().toString();

        mSpinnerScale = (Spinner) findViewById(R.id.scale_spinner_table);

        mLayOut = (LinearLayout) findViewById(R.id.ll_view);
        // LinearLayout.LayoutParams lp = n;
        if (mVideoView == null) {
            mVideoView = new VideoSurfaceView2(this);
            mLayOut.addView(mVideoView);
        }
        // 将可选内容与ArrayAdapter连接起来
        // mArraysId = R.array.scale_parameters;
        mAdapterScale = ArrayAdapter.createFromResource(this,
                R.array.scale_parameters, android.R.layout.simple_spinner_item);
        // 设置下拉列表的风格
        mAdapterScale
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 将adapter添加到spinner中
        mSpinnerScale.setAdapter(mAdapterScale);
        mSpinnerScale.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                    int arg2, long arg3) {
                Log.d(TAG, "Select " + mSpinnerScale.getItemAtPosition(arg2));
                // mFileTypePos = arg2;
                switch (arg2) {
                case 0:
                    mScale = 1.0f;
                    break;
                case 1:
                    mScale = 0.8f;
                    break;
                case 2:
                    mScale = 0.5f;
                    break;
                case 3:
                    mScale = 0.3f;
                    break;
                default:
                    mScale = 1.0f;
                    break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                mScale = 1.0f;
            }
        });

        mSpinnerEffect = (Spinner) findViewById(R.id.effect_spinner_table);

        // 将可选内容与ArrayAdapter连接起来
        // mArraysId = R.array.scale_parameters;
        String[] aArray = new String[20];
        for (int i = 0; i < 20; i++) {
            aArray[i] = Editor.getEffectCnName(i + 1);
        }

        mAdapterEffect = new ArrayAdapter(this,
                android.R.layout.simple_spinner_item, aArray);
        // 设置下拉列表的风格
        mAdapterEffect
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 将adapter添加到spinner中
        mSpinnerEffect.setAdapter(mAdapterEffect);
        mSpinnerEffect.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                    int arg2, long arg3) {
                Log.d(TAG, "Select " + mSpinnerEffect.getItemAtPosition(arg2));
                mEffectNo = arg2 + 1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                mEffectNo = Editor.EFFECT_NONE;
            }
        });

        btnStart = (Button) this.findViewById(R.id.btn_editor_start);
        btnStart.setOnClickListener(new ClickEvent(this));
        btnStop = (Button) this.findViewById(R.id.btn_preview_stop);
        btnStop.setOnClickListener(new ClickEvent(this));
        btnpreview = (Button) this.findViewById(R.id.btn_preview_start);
        btnpreview.setOnClickListener(new ClickEvent(this));
    }

    public void getEditTextValue() {
        String srcFile = mInputName.getText().toString();
        if (srcFile != null && !srcFile.isEmpty()) {
            mSrcFile = DEFAULT_PATH + srcFile;
        }

        String dtsFile = mOutputName.getText().toString();
        if (dtsFile != null && !dtsFile.isEmpty()) {
            mDtsFile = DEFAULT_PATH + dtsFile;
        }

        String sbegin = mEtBeginTime.getText().toString();
        if (sbegin != null && !sbegin.isEmpty()) {
            mBeginTime = Integer.parseInt(sbegin);
        }

        String send = mEtEndTime.getText().toString();
        if (send != null && !send.isEmpty()) {
            mEndTime = Integer.parseInt(send);
        }
    }

    public void saveMyBitmap(Bitmap mBitmap, String bitName) {
        File f = new File("/sdcard/Movies/download/" + bitName + ".jpg");
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        try {
            fOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class ClickEvent implements OnClickListener {
        private Context mContext;

        public ClickEvent(Context context) {
            mContext = context;
        }

        @Override
        public void onClick(View arg0) {
            if (arg0 == btnStart) {
                onClickEditorStart();
            } else if (arg0 == btnpreview) {
                try {
                    onClickPreviewStart();
                } catch (IllegalArgumentException | SecurityException
                        | IllegalStateException | IOException e) {
                    e.printStackTrace();
                }
            } else if (arg0 == btnStop) {
                onClickStop();
            }
        }

        private void onClickStop() {
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
            }
        }

        private void onClickPreviewStart() throws IllegalArgumentException,
                SecurityException, IllegalStateException, IOException {
            getEditTextValue();
            int width = mLayOut.getWidth();
            int height = mLayOut.getHeight();

            Log.i(TAG, "width is " + width + ", height is " + height);
            MediaMetadataRetriever mRetriever = new MediaMetadataRetriever();
            mRetriever.setDataSource(mSrcFile);
            int mInPutHeight = Integer
                    .parseInt(mRetriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int mInPutWidth = Integer
                    .parseInt(mRetriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            mRetriever.release();

            LinearLayout.LayoutParams lp = null;
            if (mInPutHeight > mInPutWidth) {
                int w = height * mInPutWidth / mInPutHeight;
                Log.i(TAG, "w is " + w + ", height is " + height);
                lp = new LinearLayout.LayoutParams(w, height);
                // mVideoView.getHolder().setFixedSize(w, height);
                // lp.setMargins(margin, 0, margin, 0);
            } else {
                int h = width * mInPutHeight / mInPutWidth;
                Log.i(TAG, "width is " + width + ", h is " + h);
                lp = new LinearLayout.LayoutParams(width, h);
                // mVideoView.getHolder().setFixedSize(width, h);
            }
            // mVideoView.setLayoutParams(lp);
            // mVideoView.getHolder().setFixedSize(mInPutWidth, mInPutHeight);
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setDataSource(mSrcFile);
                SurfaceTexture sf = mVideoView.getSurfaceTexture();
                Surface surface = new Surface(sf);
                mMediaPlayer.setSurface(surface);
                mMediaPlayer.setScreenOnWhilePlaying(true);
                surface.release();
            }
            mVideoView.changeEffect(mEffectNo, 100);
            try {
                mMediaPlayer.prepare();
            } catch (IOException t) {
                Log.e(TAG, "media player prepare failed");
            }
            mMediaPlayer.seekTo(mBeginTime * 1000);
            mMediaPlayer.start();
        }

        private void onClickEditorStart() {
            mQueryDialog = ProgressDialog.show(MainActivity.this, "",
                    "正在编辑视频，请稍后……", true);
            getEditTextValue();
            EditorThread thread = new EditorThread();
            thread.start();
        }
    }

    private class EditorThread extends Thread {
        // private Surface surface;
        private Editor editor;

        public EditorThread() {
            // this.surface = surface;
            editor = new Editor();
        }

        @Override
        public void run() {
            editor.init(mSrcFile, mDtsFile);
            editor.setScale(mScale);
            if (mEndTime != -1 || mBeginTime != 0)
                editor.setSplitTime(mBeginTime, mEndTime);
            editor.setEffect(mEffectNo, 100);
            editor.process();
/*
            ArrayList<Bitmap> thumbnail = editor.getThumbnail();
            for (int i = 0; i < thumbnail.size(); i++) {
                saveMyBitmap(thumbnail.get(i), mDtsFile + (i + 1));
            }*/
            mQueryDialog.cancel();
            mQueryDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
        super.onDestroy();
    }
}
