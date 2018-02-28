package com.tsamp.sproutsocr;

import android.Manifest;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * {@code MainActivity} takes care of all startup steps.
 * <ol>
 *     <li>Acquire permissions
 *     <ul>
 *         <li>Check for permissions</li>
 *         <li>If permission isn't yet granted, prompt user for permission</li>
 *         <li>If user denies permission, quit program.</li>
 *     </ul>
 *     </li>
 *     <li>Start Camera</li>
 * </ol>
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int PERMISSION_REQUEST_CAMERA = 1;

    private SurfaceView surfaceView;
    private GLSurfaceView glSurfaceView;
    private TextView textView;
    private Button buttonCapture;
    private Button buttonProcess;

    private CameraHandler cameraHandler;
    private Thread cameraThread;

    public MainActivity() {
        super();
    }

    // ============================= Hooks

    public void onCapture(View sender) {
        try {
            if (cameraHandler.camera.previewing()) {
                Log.i(TAG, "SNAPSHOT REQUESTED");
                cameraHandler.camera.capture();
            } else {
                Log.i(TAG, "CONTINUE PREVIEW");
                buttonCapture.setText(R.string.capture_pic);
                buttonProcess.setEnabled(false);

                cameraHandler.camera.preview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onProcess(View sender) {

    }

    // ============================= FragmentActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        glSurfaceView = findViewById(R.id.glView);
        textView = findViewById(R.id.textView);
        buttonCapture = findViewById(R.id.button_capture);
        buttonProcess = findViewById(R.id.button_process);

        cameraHandler = new CameraHandler();
        cameraThread = new Thread(cameraHandler);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // check permissions
        int grant = PermissionChecker.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);
        switch (grant) {
            case PermissionChecker.PERMISSION_GRANTED: // start camera stuffs
                cameraThread.start();
                break;
            case PermissionChecker.PERMISSION_DENIED_APP_OP: // user has disabled permission -> request
            case PermissionChecker.PERMISSION_DENIED: // permission not yet granted -> request
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            cameraHandler.camera.close();
        } catch (Exception e) {e.printStackTrace();}
    }

    @Override
    protected void onStop() {
        super.onStop();

        cameraHandler.handler.post(new Runnable() {
            @Override
            public void run() {
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CAMERA:
                if (!permissions[0].equals(Manifest.permission.CAMERA)) {
                    throw new IllegalStateException();
                }
                if (grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
                    Log.i(TAG, "permission granted ");
                    cameraThread.start();
                } else {
                    finish();
                }
                break;
            default:
                throw new IllegalStateException();
        }
    }

    private class CameraHandler implements Runnable, CameraWrapper.Callback {

        CameraWrapper camera;
        private Handler handler;

        CameraHandler() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera = new Camera2Wrapper(getApplicationContext(), surfaceView, this);
            } else {
                camera = new LegacyCameraWrapper(surfaceView, this);
            }
            handler = null;
        }

        // ============================= Runnable

        @Override
        public void run() {
            // prep the Looper and Handler ASAP so we can start queueing messages.
            Looper.prepare();
            Looper myLooper = Looper.myLooper();
            handler = new Handler(myLooper);

            try {
                Log.i(TAG, "opening");
                camera.open(0);

                Log.i(TAG, "waiting");
                while (!camera.cameraReady()) {
                    Thread.sleep(10);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setVisibility(View.INVISIBLE);
                    }
                });
                // camera ready
                Log.i(TAG, "ready to preview");

                camera.preview();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // after initial camera setup, dedicate this Thread to receiving messages.
            Looper.loop();
        }

        // ============================= CameraWrapper.Callback

        @Override
        public void onImageCaptured(Bitmap bmp) {
            Log.i(TAG, "image captured");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    buttonCapture.setText(R.string.retake_pic);
                    buttonProcess.setEnabled(true);
                }
            });
        }
    }
}
