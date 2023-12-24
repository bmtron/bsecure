package com.example.bsecure;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.telecom.Conference;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BCamera extends Fragment {
    private Activity parent;
    private int camId;
    private ActivityResultLauncher<String> requestSinglePermissionLauncher;
    private CameraDevice cameraDevice;
    private Semaphore mCameraOpenCloseLock;
    private Integer mSensorOrientation;
    private Size mVideoSize;
    private Size mPreviewSize;
    private BTextureView mTexturePreview;
    private MediaRecorder mMediaRecorder;
    private ActivityResultLauncher<String> activityResultLauncher;
    private CameraDevice.StateCallback mStateCallback;
    private CameraDevice mCamera;
    private CaptureRequest.Builder mPreviewBuilder;
    private File mCurrentFile;
    public BCamera(Activity activity) {
        this(activity, null);
    }
    public BCamera(Activity activity, ActivityResultLauncher<String> activityResultLauncher) {
        parent = activity;
        mTexturePreview = new BTextureView(activity.getApplicationContext());
        this.activityResultLauncher = activityResultLauncher;
    }
    private void openCamera(int width, int height) {
        if (parent == null || parent.isFinishing()) {
            return;
        }
        CameraManager cameraManager = (CameraManager) parent.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d("camera", "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening");
            }
            setBackCamID();
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(String.valueOf(camId));
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, mVideoSize);
            int orientation = getResources().getConfiguration().orientation;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTexturePreview.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTexturePreview.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            //TODO: configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                PermissionCheck perms = new PermissionCheck(parent.getApplicationContext(), null, activityResultLauncher);
                perms.checkSingularPermission(Manifest.permission.CAMERA);
                return;
            }
            setupStateCallback();
            cameraManager.openCamera(String.valueOf(camId), mStateCallback, null);
        } catch (Exception e) {
            Log.e("camera", "FAIL");
        }
    }

    private void setBackCamID() {
        CameraManager cameraManager = (CameraManager) parent.getSystemService(Context.CAMERA_SERVICE);
        String[] availableCameras;
        try {
            availableCameras = cameraManager.getCameraIdList();
            for (int i = 0; i < availableCameras.length; i++) {
                CameraCharacteristics cam = cameraManager.getCameraCharacteristics(availableCameras[i]);
                if (cam.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    camId = i;
                }
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }
    private void setupStateCallback() {
        mStateCallback = new CameraDevice.StateCallback() {

            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                mCamera = camera;
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                mCamera.close();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                mCamera.close();
            }
        };
    }
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (1920 == size.getWidth() && 1080 == size.getHeight()) {
                return size;
            }
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e("camerasize", "Couldn't find suitable size");
        return choices[choices.length - 1];

    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    private void startPreview() {
        /*if (mCamera == null || !mTexturePreview.isAvailable() || mPreviewSize == null) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTexturePreview.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);
            mCamera.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }*/
    }
    private void setupMediaRecorder() throws IOException {
/*        if (parent == null) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mCurrentFile = getOutputMediaFile();

        mMediaRecorder.setOutputFile(mCurrentFile.getAbsolutePath());
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
        int rotation = parent.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS);
        }*/
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }


}
