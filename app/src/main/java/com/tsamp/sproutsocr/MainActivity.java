package com.tsamp.sproutsocr;

import android.Manifest;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

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

    private SurfaceView view;

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
                cameraHandler.camera.preview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================= FragmentActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        view = findViewById(R.id.surfaceView);
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

        CameraHandler() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera = new Camera2Wrapper(getApplicationContext(), view, this);
            } else {
                camera = new LegacyCameraWrapper(view, this);
            }
        }

        // ============================= Runnable

        @Override
        public void run() {
            try {
                Log.i(TAG, "opening");
                camera.open(0);

                Log.i(TAG, "waiting");
                while (!camera.cameraReady()) {
                    Thread.sleep(10);
                }
                // camera ready
                Log.i(TAG, "ready to preview");

                camera.preview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ============================= CameraWrapper.Callback

        @Override
        public void onImageCaptured(Image capturedImage) {

        }
    }
}
