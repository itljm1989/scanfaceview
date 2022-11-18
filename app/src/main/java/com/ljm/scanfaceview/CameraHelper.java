package com.ljm.scanfaceview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.nio.file.ClosedFileSystemException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class CameraHelper {
    private static final String TAG = "CameraHelper";
    // 预览宽度
    private static final int PREVIEW_WIDTH = 1080;
    // 预览高度
    private static final int PREVIEW_HEIGHT = 1440;

    private Activity mActivity;

    private CustomTextureView mTextureView;

    private HandlerThread handlerThread = new HandlerThread("CameraThread");

    private Handler mCameraHandler;
    private Size mPreviewSize;
    private CameraManager mCameraManager;

    // 默认使用前置摄像头
    private int mCameraFacing = CameraCharacteristics.LENS_FACING_FRONT;

    private String mCameraId = "0";

    private CameraCharacteristics mCameraCharacteristics;
    private int mCameraSensorOrientation;
    private int mDisplayRotation;
    // 人脸检测模式
    private int mFaceDetectMode = CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF;
    // 人脸检测坐标转换矩阵
    private Matrix mFaceDetectMatrix = new Matrix();

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    // 保存人脸坐标信息
    private List mFacesRect = new ArrayList<RectF>();
    private FaceDetectListener mFaceDetectListener;
    //是否可以拍照
    private boolean canTakePic = true;
    //是否可以切换摄像头
    private boolean canExchangeCamera = false;                                             //是否可以切换摄像头

    public CameraHelper(Activity activity, CustomTextureView textureView) {
        this.mActivity = activity;
        this.mTextureView = textureView;
        init();
    }

    private void init() {
        handlerThread.start();
        mCameraHandler = new Handler(handlerThread.getLooper());
        mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        mDisplayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                Log.i(TAG, "width=" + width + "//height=" + height);
                configureTransform(width, height);
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                releaseCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                configureTransform(width, height);
                initCameraInfo();
            }
        });
    }

    private void initCameraInfo() {
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            if (cameraIdList == null || cameraIdList.length == 0) {
                Toast.makeText(mActivity, "没有可用相机", Toast.LENGTH_SHORT).show();
                return;
            }
            for (String id : cameraIdList) {
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(id);
                int facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == mCameraFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = cameraCharacteristics;
                }
                Log.i(TAG, "设备中的摄像头:" + id);
            }

            int supportLevel = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                Toast.makeText(mActivity, "相机硬件不支持新特性", Toast.LENGTH_SHORT).show();
            }
            // 获取摄像头方向
            mCameraSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            // 获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
            StreamConfigurationMap configurationMap = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] previewSize = configurationMap.getOutputSizes(SurfaceTexture.class);

            boolean exchange = exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation);

            mPreviewSize = getBestSize(
                    exchange ? mPreviewSize.getHeight() : mPreviewSize.getWidth(),
                    exchange ? mPreviewSize.getWidth() : mPreviewSize.getHeight(),
                    exchange ? mTextureView.getHeight() : mTextureView.getWidth(),
                    exchange ? mTextureView.getWidth() : mTextureView.getHeight(),
                    Arrays.asList(previewSize)
            );
            mTextureView.getSurfaceTexture().setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Log.i(TAG, "预览最优尺寸 ：" + mPreviewSize.getWidth() + " * " + mPreviewSize.getHeight() + ", 比例  " + (float) mPreviewSize.getWidth() / mPreviewSize.getHeight());

            // 根据预览的尺寸大小调整TextureView的大小，保证画面不被拉伸
            int orientation = mActivity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            initFaceDetect();
            openCamera();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                    createCaptureSession(cameraDevice);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.i(TAG, "onDisconnected");
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    Log.e(TAG, "onError");
                    Toast.makeText(mActivity, "打开相机失败!", Toast.LENGTH_SHORT).show();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建预览会话
     *
     * @param cameraDevice
     */
    private void createCaptureSession(CameraDevice cameraDevice) {
        try {
            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = new Surface(mTextureView.getSurfaceTexture());
            captureRequestBuilder.addTarget(surface);
            // 闪光灯
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            if (mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
                // 人脸检测
                captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);
            }
            // 为相机预览，创建一个CameraCaptureSession对象
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    try {
                        session.setRepeatingRequest(captureRequestBuilder.build(), mCaptureCallBack, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(mActivity, "开启预览会话失败", Toast.LENGTH_SHORT).show();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 处理人脸信息
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallBack = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
                handleFaces(result);
            }
            canExchangeCamera = true;
            canTakePic = true;
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG, "onCaptureFailed");
            Toast.makeText(mActivity, "开启预览失败", Toast.LENGTH_SHORT).show();
        }
    };

    private void handleFaces(TotalCaptureResult result) {
        Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
        mFacesRect.clear();
        if (faces != null) {
            for (Face face : faces) {
                Rect bounds = face.getBounds();
                int left = bounds.left;
                int top = bounds.top;
                int right = bounds.right;
                int bottom = bounds.bottom;
                RectF rawFaceRect = new RectF(left, top, right, bottom);
                mFaceDetectMatrix.mapRect(rawFaceRect);
                RectF resultFaceRect;
                if (mCameraFacing == CaptureRequest.LENS_FACING_FRONT) {
                    resultFaceRect = rawFaceRect;
                } else {
                    resultFaceRect = new RectF(rawFaceRect.left, rawFaceRect.top - mPreviewSize.getWidth(), rawFaceRect.right, rawFaceRect.bottom - mPreviewSize.getWidth());
                }
                mFacesRect.add(resultFaceRect);
                Log.i(TAG, "原始人脸位置: " + bounds.width() + " * " + bounds.height() + "  " + bounds.left + " " + bounds.top + " " + bounds.right + " " + bounds.bottom + "分数: " + face.getScore());
                Log.i(TAG, "转换后人脸位置: " + resultFaceRect.width() + " * " + resultFaceRect.height() + "  " + resultFaceRect.left + " " + resultFaceRect.top + " " + resultFaceRect.right + " " + resultFaceRect.bottom + "   分数: " + face.getScore());
            }
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFaceDetectListener != null) {
                    mFaceDetectListener.onFaceDetect(mFacesRect);
                }

            }
        });
    }

    public void setFaceDetectListener(FaceDetectListener listener) {
        this.mFaceDetectListener = listener;
    }

    private void initFaceDetect() {
        // 同时检测到人脸的数量
        int faceDetectCount = mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
        // 人脸检测的模式
        int[] faceDetectModes = mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        if (faceDetectModes == null) {
            Toast.makeText(mActivity, "相机硬件不支持人脸检测", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Integer> faceDetectModeList = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            faceDetectModeList = Arrays.stream(faceDetectModes).boxed().collect(Collectors.toList());
        }
        if (faceDetectModeList.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL)) {
            mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        } else if (faceDetectModeList.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)) {
            mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        } else {
            mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
        }
        if (mFaceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
            Toast.makeText(mActivity, "相机硬件不支持人脸检测", Toast.LENGTH_SHORT).show();
            return;
        }
        Rect activeArraySizeRect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        float scaledWidth = mPreviewSize.getWidth() / (float) activeArraySizeRect.width();
        float scaledHeight = mPreviewSize.getHeight() / (float) activeArraySizeRect.height();
        boolean mirror = mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT;

        mFaceDetectMatrix.setRotate(mCameraSensorOrientation);
        mFaceDetectMatrix.postScale(mirror ? -scaledWidth : scaledWidth, scaledHeight);
        if (exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation)) {
            mFaceDetectMatrix.postTranslate(mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }
        Log.i(TAG, "成像区域  " + activeArraySizeRect.width() + "//" + activeArraySizeRect.height() + " 比例: " + (float) activeArraySizeRect.width() / activeArraySizeRect.height());
        Log.i(TAG, "预览区域  " + mPreviewSize.getWidth() + "//" + mPreviewSize.getHeight() + " 比例 " + (float) mPreviewSize.getWidth() / mPreviewSize.getHeight());

        for (Integer mode : faceDetectModeList) {
            Log.i(TAG, "支持的人脸检测模式" + mode);
        }

        Log.i(TAG, "同时检测到人脸的数量 " + faceDetectCount);
    }

    /**
     * 根据提供的屏幕方向 [displayRotation] 和相机方向 [sensorOrientation] 返回是否需要交换宽高
     */
    private boolean exchangeWidthAndHeight(int displayRotation, int sensorOrientation) {
        boolean exchange = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    exchange = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    exchange = true;
                }
                break;
            default:
                Log.i(TAG, "Display rotation is invalid: $displayRotation");
                break;
        }
        Log.i(TAG, "屏幕方向  " + displayRotation);
        Log.i(TAG, "相机方向  " + sensorOrientation);
        return exchange;
    }

    private Size getBestSize(int targetWidth, int targetHeight, int maxWidth, int maxHeight, List<Size> sizeList) {
        // 比指定宽高大的Size列表
        ArrayList<Size> bigEnough = new ArrayList<>();
        // 比指定宽高小的Size列表
        ArrayList<Size> notBigEnough = new ArrayList<>();
        for (Size size : sizeList) {
            //宽<=最大宽度  &&  高<=最大高度  &&  宽高比 == 目标值宽高比
            if (size.getWidth() <= maxWidth && size.getHeight() <= maxHeight
                    && size.getWidth() == size.getHeight() * targetWidth / targetHeight) {
                if (size.getWidth() >= targetWidth && size.getHeight() >= targetHeight) {
                    bigEnough.add(size);
                } else {
                    notBigEnough.add(size);
                }
            }
            Log.i(TAG, "系统支持的尺寸: " + size.getWidth() + "*" + size.getHeight() + ",  比例 ：" + ((float) size.getWidth() / size.getHeight()));
        }
        Log.i(TAG, "最大尺寸 ：" + maxWidth + "*" + maxHeight + ", 比例 ：" + (float) targetWidth / targetHeight);
        Log.i(TAG, "目标尺寸 ：" + targetWidth + "*" + targetHeight + ", 比例 ：" + (float) targetWidth / targetHeight);
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return sizeList.get(0);
        }
    }

    public void releaseCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    public void releaseThread() {
        try {
            handlerThread.quitSafely();
            handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth()
            );
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate((float) (90 * (rotation - 2)), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
        Log.i(TAG, "viewWidth=" + viewWidth + "//viewHeight=" + viewHeight);
    }

    /**
     * 切换摄像头
     */
    public void exchangeCamera() {
        if (mCameraDevice == null || !canExchangeCamera || mTextureView.isAvailable()) {
            return;
        }
        if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            mCameraFacing = CameraCharacteristics.LENS_FACING_BACK;
        } else {
            mCameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
        }
        //重置预览大小
        mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        releaseCamera();
        initCameraInfo();
    }

    private class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size size1, Size size2) {
            return java.lang.Long.signum((long) size1.getWidth() * size1.getHeight() - (long) size2.getWidth() * size2.getHeight());
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

    interface FaceDetectListener {
        void onFaceDetect(List<RectF> facesRect);
    }

}
