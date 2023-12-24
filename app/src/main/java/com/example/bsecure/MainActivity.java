package com.example.bsecure;

import android.content.ContentValues;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;

import com.example.bsecure.databinding.ActivityMainBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private ActivityMainBinding viewBinding;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), map -> {
                    for (Map.Entry<String, Boolean> entry : map.entrySet()) {
                        if (!entry.getValue()) {
                            Toast toast = Toast.makeText(getApplicationContext(), entry.getKey() + ": Permission not granted.", Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    }
                });

        PermissionCheck permChecker = new PermissionCheck(getApplicationContext(), requestPermissionLauncher);
        permChecker.checkPermissions();

        cameraExecutor = Executors.newSingleThreadExecutor();
        Button startRecording = (Button) findViewById(R.id.video_capture_button);
        viewBinding.videoCaptureButton.setEnabled(true);
        viewBinding.videoCaptureButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                captureVideo();
            }
        });

        try {
            startCamera();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void takePhoto() {}
    private void captureVideo() {
        if (videoCapture == null) {
            return;
        }
        viewBinding.videoCaptureButton.setEnabled(false);

        Recording curRecording = recording;
        if (curRecording != null) {
            curRecording.stop();
            recording = null;
            return;
        }
        String name = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues).build();
        recording = videoCapture.getOutput()
                .prepareRecording(this, mediaStoreOutputOptions)
                .start(ContextCompat.getMainExecutor(this), recordEvent -> {
                    if (recordEvent instanceof VideoRecordEvent.Start) {
                        viewBinding.videoCaptureButton.setEnabled(true);
                        viewBinding.videoCaptureButton.setText(getString(R.string.stop_capture));
                    }
                    if (recordEvent instanceof VideoRecordEvent.Finalize) {
                        if (!((VideoRecordEvent.Finalize) recordEvent).hasError()) {
                            String msg = "Video capture succeeded: " + ((VideoRecordEvent.Finalize) recordEvent).getOutputResults().getOutputUri();
                            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                        } else {
                            if (recording != null) {
                                recording.close();
                                recording = null;
                            }
                        }
                        viewBinding.videoCaptureButton.setText(getString(R.string.start_capture));
                        viewBinding.videoCaptureButton.setEnabled(true);
                    }
                });

    }
    private void startCamera() throws ExecutionException, InterruptedException {
        ProcessCameraProvider cameraProviderFuture = ProcessCameraProvider.getInstance(this).get();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(viewBinding.viewFinder.getSurfaceProvider());
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
        videoCapture = VideoCapture.withOutput(recorder);
        cameraProviderFuture.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
        try {
            cameraProviderFuture.unbindAll();
            cameraProviderFuture.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture);
        } catch (Exception e) {

        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}