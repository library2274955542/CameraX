package com.kserjhfnkedjfv.camerax.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.kserjhfnkedjfv.camerax.R;
import com.kserjhfnkedjfv.camerax.util.CameraConstant;
import com.kserjhfnkedjfv.camerax.util.CameraParam;
import com.kserjhfnkedjfv.camerax.util.FocusView;
import com.kserjhfnkedjfv.camerax.util.NoPermissionException;
import com.kserjhfnkedjfv.camerax.util.Tools;

import java.io.File;
import java.util.concurrent.TimeUnit;


public class CameraActivity extends AppCompatActivity {

    private static String TAG = "CameraActivity";
    private PreviewView mPreviewView;
    private ImageView mImgSwitch;
    private LinearLayout mLlPictureParent;
    private ImageView mImgPicture;
    private FocusView mFocusView;
    private View mViewMask;
    private RelativeLayout mRlResultPicture;
    private ImageView mImgPictureCancel;
    private ImageView mImgPictureSave;
    private RelativeLayout mRlStart;
    private TextView mTvBack;
    private ImageView mImgTakePhoto;
    private ImageCapture mImageCapture;
    private CameraControl mCameraControl;
    private ProcessCameraProvider mCameraProvider;
    private CameraParam mCameraParam;
    private boolean mFront;
    private Handler mTimerDisposable;
    private ConstraintSet mConstraintSet = new ConstraintSet();
    private int mTimer = 0xaa;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);
        mCameraParam = getIntent().getParcelableExtra(CameraConstant.CAMERA_PARAM_KEY);
        if (mCameraParam == null) {
            throw new IllegalArgumentException("CameraParam is null");
        }
        if (!Tools.checkPermission(this)) {
            throw new NoPermissionException("Need to have permission to take pictures and storage");
        }
        mFront = mCameraParam.isFront();
        initView();
        setViewParam();
        intCamera();
        initMaskDimensionRatio();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoFocusCancel();
        mCameraProvider.unbindAll();
    }

    private void autoFocusCancel() {
        if (mTimerDisposable != null) {
            mTimerDisposable.removeMessages(mTimer);
        }
    }

    /**
     * 设置裁剪区域的比例
     */
    private void initMaskDimensionRatio() {
        String maskDimensionRatio = mCameraParam.getMaskDimensionRatio();
        if (!maskDimensionRatio.isEmpty()) {
            WindowManager mgr = ((WindowManager) getSystemService(Context.WINDOW_SERVICE));
            int lastOrientation;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lastOrientation = getDisplay().getRotation();
            } else {
                lastOrientation = mgr.getDefaultDisplay().getRotation();
            }
            if (lastOrientation == 1 || lastOrientation == 3) {
                maskDimensionRatio = maskDimensionRatio.replaceAll("h", "w");
            } else {
                maskDimensionRatio = maskDimensionRatio.replaceAll("w", "h");
            }
            try {
                ConstraintLayout constraintLayout = findViewById(R.id.max_layout);
                mConstraintSet.clone(constraintLayout);
                mConstraintSet.setDimensionRatio(R.id.view_mask, maskDimensionRatio);
                mConstraintSet.applyTo(constraintLayout);
            } catch (Exception e) {
                Log.e(TAG, "onRestoreInstanceState: ", e);
            }

        }
    }

    private void setViewParam() {
        //是否显示切换按钮
        if (mCameraParam.isShowSwitch()) {
            mImgSwitch.setVisibility(View.VISIBLE);
            if (mCameraParam.getSwitchSize() != -1 || mCameraParam.getSwitchLeft() != -1 || mCameraParam.getSwitchTop() != -1) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) mImgSwitch.getLayoutParams();
                if (mCameraParam.getSwitchSize() != -1) {
                    layoutParams.width = layoutParams.height = mCameraParam.getSwitchSize();
                }
                if (mCameraParam.getSwitchLeft() != -1) {
                    layoutParams.leftMargin = mCameraParam.getSwitchLeft();
                }
                if (mCameraParam.getSwitchTop() != -1) {
                    layoutParams.topMargin = mCameraParam.getSwitchTop();
                }
                mImgSwitch.setLayoutParams(layoutParams);
            }
            if (mCameraParam.getSwitchImgId() != -1) {
                mImgSwitch.setImageResource(mCameraParam.getSwitchImgId());
            }
        } else {
            mImgSwitch.setVisibility(View.GONE);
        }

        //是否显示裁剪框
        if (mCameraParam.isShowMask()) {
            mViewMask.setVisibility(View.VISIBLE);
            if (mCameraParam.getMaskMarginLeftAndRight() != -1 || mCameraParam.getMaskMarginTop() != -1
                    || mCameraParam.getMaskRatioH() != -1) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) mViewMask.getLayoutParams();

                if (mCameraParam.getMaskMarginLeftAndRight() != -1) {
                    layoutParams.leftMargin = layoutParams.rightMargin = mCameraParam.getMaskMarginLeftAndRight();
                }

                if (mCameraParam.getMaskMarginTop() != -1) {
                    layoutParams.topMargin = mCameraParam.getMaskMarginTop();
                }

                if (mCameraParam.getMaskRatioH() != -1) {
                    Tools.reflectMaskRatio(mViewMask, mCameraParam.getMaskRatioW(), mCameraParam.getMaskRatioH());
                }
                mViewMask.setLayoutParams(layoutParams);
            }
            if (mCameraParam.getMaskImgId() != -1) {
                mViewMask.setBackgroundResource(mCameraParam.getMaskImgId());
            }
        } else {
            mViewMask.setVisibility(View.GONE);
        }

        if (mCameraParam.getBackText() != null) {
            mTvBack.setText(mCameraParam.getBackText());
        }
        if (mCameraParam.getBackColor() != -1) {
            mTvBack.setTextColor(mCameraParam.getBackColor());
        }
        if (mCameraParam.getBackSize() != -1) {
            mTvBack.setTextSize(mCameraParam.getBackSize());
        }

        if (mCameraParam.getTakePhotoSize() != -1) {
            int size = mCameraParam.getTakePhotoSize();

            ViewGroup.LayoutParams pictureCancelParams = mImgPictureCancel.getLayoutParams();
            pictureCancelParams.width = pictureCancelParams.height = size;
            mImgPictureCancel.setLayoutParams(pictureCancelParams);

            ViewGroup.LayoutParams pictureSaveParams = mImgPictureSave.getLayoutParams();
            pictureSaveParams.width = pictureSaveParams.height = size;
            mImgPictureSave.setLayoutParams(pictureSaveParams);

            ViewGroup.LayoutParams takePhotoParams = mImgTakePhoto.getLayoutParams();
            takePhotoParams.width = takePhotoParams.height = size;
            mImgTakePhoto.setLayoutParams(takePhotoParams);
        }

        mFocusView.setParam(mCameraParam.getFocusViewSize(), mCameraParam.getFocusViewColor(),
                mCameraParam.getFocusViewTime(), mCameraParam.getFocusViewStrokeSize(), mCameraParam.getCornerViewSize());


        if (mCameraParam.getCancelImgId() != -1) {
            mImgPictureCancel.setImageResource(mCameraParam.getCancelImgId());
        }
        if (mCameraParam.getSaveImgId() != -1) {
            mImgPictureSave.setImageResource(mCameraParam.getSaveImgId());
        }
        if (mCameraParam.getTakePhotoImgId() != -1) {
            mImgTakePhoto.setImageResource(mCameraParam.getTakePhotoImgId());
        }

        if (mCameraParam.getResultBottom() != -1) {
            ConstraintLayout.LayoutParams resultPictureParams = (ConstraintLayout.LayoutParams) mRlResultPicture.getLayoutParams();
            resultPictureParams.bottomMargin = mCameraParam.getResultBottom();
            mRlResultPicture.setLayoutParams(resultPictureParams);

            ConstraintLayout.LayoutParams startParams = (ConstraintLayout.LayoutParams) mRlStart.getLayoutParams();
            startParams.bottomMargin = mCameraParam.getResultBottom();
            mRlStart.setLayoutParams(startParams);
        }

        if (mCameraParam.getResultLeftAndRight() != -1) {
            RelativeLayout.LayoutParams pictureCancelParams = (RelativeLayout.LayoutParams) mImgPictureCancel.getLayoutParams();
            pictureCancelParams.leftMargin = mCameraParam.getResultLeftAndRight();
            mImgPictureCancel.setLayoutParams(pictureCancelParams);

            RelativeLayout.LayoutParams pictureSaveParams = (RelativeLayout.LayoutParams) mImgPictureSave.getLayoutParams();
            pictureSaveParams.rightMargin = mCameraParam.getResultLeftAndRight();
            mImgPictureSave.setLayoutParams(pictureSaveParams);
        }

        if (mCameraParam.getBackLeft() != -1) {
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mTvBack.getLayoutParams();
            layoutParams.leftMargin = mCameraParam.getBackLeft();
            mTvBack.setLayoutParams(layoutParams);
        }
        Tools.reflectPreviewRatio(mPreviewView, Tools.aspectRatio(this));
    }

    private void initView() {
        mPreviewView = findViewById(R.id.previewView);
        mImgSwitch = findViewById(R.id.img_switch);
        mLlPictureParent = findViewById(R.id.ll_picture_parent);
        mImgPicture = findViewById(R.id.img_picture);
        mFocusView = findViewById(R.id.focus_view);
        mViewMask = findViewById(R.id.view_mask);
        mRlResultPicture = findViewById(R.id.rl_result_picture);
        mImgPictureCancel = findViewById(R.id.img_picture_cancel);
        mImgPictureSave = findViewById(R.id.img_picture_save);
        mRlStart = findViewById(R.id.rl_start);
        mTvBack = findViewById(R.id.tv_back);
        mImgTakePhoto = findViewById(R.id.img_take_photo);

        //切换相机
        mImgSwitch.setOnClickListener(v -> {
            switchOrition();
            bindCameraUseCases();
        });

        //拍照成功然后点取消
        mImgPictureCancel.setOnClickListener(v -> {
            mImgPicture.setImageBitmap(null);
            mRlStart.setVisibility(View.VISIBLE);
            mRlResultPicture.setVisibility(View.GONE);
            mLlPictureParent.setVisibility(View.GONE);
            autoFocusTimer();
        });
        //拍照成功然后点保存
        mImgPictureSave.setOnClickListener(v -> {
            savePicture();
        });
        //还没拍照就点取消
        mTvBack.setOnClickListener(v -> {
            finish();
        });
        //点击拍照
        mImgTakePhoto.setOnClickListener(v -> {
            takePhoto(mCameraParam.getPictureTempPath());
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            autoFocus((int) event.getX(), (int) event.getY(), false);
        }
        return super.onTouchEvent(event);
    }

    //https://developer.android.com/training/camerax/configuration
    private void autoFocus(int x, int y, boolean first) {
//        MeteringPointFactory factory = previewView.getMeteringPointFactory();
        MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(x, y);
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
//                .disableAutoCancel()
//                .addPoint(point2, FocusMeteringAction.FLAG_AE)
                // 3秒内自动调用取消对焦
                .setAutoCancelDuration(mCameraParam.getFocusViewTime(), TimeUnit.SECONDS)
                .build();
//        mCameraControl.cancelFocusAndMetering();
        ListenableFuture<FocusMeteringResult> future = mCameraControl.startFocusAndMetering(action);
        future.addListener(() -> {
            try {
                FocusMeteringResult result = future.get();
                if (result.isFocusSuccessful()) {
//                    focus_view.showFocusView(x, y);
                    if (!first && mCameraParam.isShowFocusTips()) {
                        Toast mToast = Toast.makeText(getApplicationContext(), mCameraParam.getFocusSuccessTips(this), Toast.LENGTH_LONG);
                        mToast.setGravity(Gravity.CENTER, 0, 0);
                        mToast.show();
                    }
                } else {
                    if (mCameraParam.isShowFocusTips()) {
                        Toast mToast = Toast.makeText(getApplicationContext(), mCameraParam.getFocusFailTips(this), Toast.LENGTH_LONG);
                        mToast.setGravity(Gravity.CENTER, 0, 0);
                        mToast.show();
                    }
                    mFocusView.hideFocusView();
                }
            } catch (Exception e) {
                e.printStackTrace();
                mFocusView.hideFocusView();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void switchOrition() {
        if (mFront) {
            mFront = false;
        } else {
            mFront = true;
        }
    }

    private void intCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                mCameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.d(TAG, e.toString());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        int screenAspectRatio = Tools.aspectRatio(this);
        int rotation = mPreviewView.getDisplay() == null ? Surface.ROTATION_0 : mPreviewView.getDisplay().getRotation();

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();

        mImageCapture = new ImageCapture.Builder()
                //优化捕获速度，可能降低图片质量
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();
        // 在重新绑定之前取消绑定用例
        mCameraProvider.unbindAll();
        int cameraOrition = mFront ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraOrition).build();
        Camera camera = mCameraProvider.bindToLifecycle(this, cameraSelector, preview, mImageCapture);
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());
        mCameraControl = camera.getCameraControl();
//        mCameraInfo = camera.getCameraInfo();
//        autoFocus(outLocation[0] + (view_mask.getMeasuredWidth()) / 2, outLocation[1] + (view_mask.getMeasuredHeight()) / 2, true);
        autoFocusTimer();
    }

    private void autoFocusTimer() {
        int[] outLocation = Tools.getViewLocal(mViewMask);
        autoFocusCancel();
        mTimerDisposable = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                if (msg.what == mTimer) {
                    autoFocus(outLocation[0] + (mViewMask.getMeasuredWidth()) / 2, outLocation[1] + (mViewMask.getMeasuredHeight()) / 2, true);
                    mTimerDisposable.sendEmptyMessageDelayed(mTimer, 10000);
                }
            }
        };
        mTimerDisposable.sendEmptyMessage(mTimer);
    }

    private void takePhoto(String photoFile) {
        // 保证相机可用
        if (mImageCapture == null) {
            return;
        }

        autoFocusCancel();

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(new File(photoFile)).build();

        //  设置图像捕获监听器，在拍照后触发
        mImageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        mRlStart.setVisibility(View.GONE);
                        mRlResultPicture.setVisibility(View.VISIBLE);
                        mLlPictureParent.setVisibility(View.VISIBLE);
                        Bitmap bitmap = Tools.bitmapClip(CameraActivity.this, photoFile, mFront);
                        mImgPicture.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "onError: ", exception);
                    }
                });
    }

    private void savePicture() {
        Rect rect = null;
        if (mCameraParam.isShowMask()) {
            int[] outLocation = Tools.getViewLocal(mViewMask);
            rect = new Rect(outLocation[0], outLocation[1],
                    mViewMask.getMeasuredWidth(), mViewMask.getMeasuredHeight());
        }
        Tools.saveBitmap(this, mCameraParam.getPictureTempPath(), mCameraParam.getPicturePath(), rect, mFront);
        Tools.deletTempFile(mCameraParam.getPictureTempPath());

        Intent intent = new Intent();
        intent.putExtra(CameraConstant.PICTURE_PATH_KEY, mCameraParam.getPicturePath());
        setResult(RESULT_OK, intent);
        finish();
    }
}
