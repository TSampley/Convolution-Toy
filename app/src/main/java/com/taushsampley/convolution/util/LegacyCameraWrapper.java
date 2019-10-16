package com.taushsampley.convolution.util;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;

import androidx.annotation.NonNull;

/**
 * @author taushsampley
 */

public class LegacyCameraWrapper implements CameraWrapper {

    private static final String TAG = "LegacyCameraWrapper";

    private Camera camera;
    private SurfaceHolder surfaceHolder;

    private boolean previewing;
    private ShutterCallback shutterCallback;
    private RawCallback rawCallback;
    private PostCallback postCallback;
    private JpegCallback jpegCallback;

    private final SnapshotCallback snapshotCallback;

    public LegacyCameraWrapper(@NonNull SurfaceView view, @NonNull Handler cameraHandler,
                        @NonNull SnapshotCallback snapshotCallback) {
        camera = null;
        surfaceHolder = null;

        previewing = false;
        shutterCallback = null;
        rawCallback = null;
        postCallback = null;

        this.snapshotCallback = snapshotCallback;

        view.getHolder().addCallback(new SurfaceCallback());
    }

    private void configCamera() {
        boolean cameraOpened = camera != null;
        boolean surfaceViewLoaded = surfaceHolder != null;

        if (cameraOpened && surfaceViewLoaded) {
            try {
                camera.setPreviewDisplay(surfaceHolder);

                shutterCallback = new ShutterCallback();
                rawCallback = new RawCallback();
                postCallback = new PostCallback();
                jpegCallback = new JpegCallback();
            } catch (Exception e) {e.printStackTrace();}
        }
//        camera.setDisplayOrientation(0);
    }

    // ============================= CameraWrapper

    @Override
    public boolean canOpen() {
        // TODO: consider implementing fully
        return false;
    }

//    @Override
//    public String[] cameraList() {
//        int numCams = Camera.getNumberOfCameras();
//        String[] cameras = new String[numCams];
//
//        Camera.CameraInfo info = new Camera.CameraInfo();
//        for (int i = 0; i < numCams; i++) {
//            Camera.getCameraInfo(i, info);
//            cameras[i] = (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK ? "Back Facing " : "Front Facing ") +
//                    info.orientation + "º " + (info.canDisableShutterSound ? "shutter optional" : "shutter required");
//        }
//
//        return cameras;
//    }

    @Override
    public void open(int id) throws Exception {
        Log.i(TAG, "open requested");
        camera = Camera.open(id);
        configCamera();
    }

    @Override
    public int createSurfaceTexture(int imageUnit) {
        return -1;
    }

    @Override
    public synchronized void preview() throws Exception {
        camera.startPreview();
        previewing = true;
    }

    @Override
    public synchronized boolean previewing() {
        return previewing;
    }

    @Override
    public void capture() throws Exception {
        camera.takePicture(shutterCallback, rawCallback, postCallback, jpegCallback);
    }

    @Override
    public SurfaceTexture getSurfaceTexture() {
        return null;
    }

    @Override
    public int getTextureWidth() {
        return 0;
    }

    @Override
    public int getTextureHeight() {
        return 0;
    }

    @Override
    public void close() throws Exception {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surface created");
            surfaceHolder = holder;
            configCamera();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surface changed");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surface destroyed");
        }
    }

    private class ShutterCallback implements Camera.ShutterCallback {
        @Override
        public void onShutter() {
            Log.i(TAG, "shutter activated");
            synchronized (this) {
                previewing = false;
            }
        }
    }

    private class RawCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "raw picture taken");
        }
    }

    private class PostCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "post picture received");
        }
    }

    private class JpegCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "jpeg received");

            ByteBuffer imageData = ByteBuffer.allocate(data.length);
            imageData.put(data);
            snapshotCallback.onImageCaptured(imageData, 0, 0);
        }
    }
}
