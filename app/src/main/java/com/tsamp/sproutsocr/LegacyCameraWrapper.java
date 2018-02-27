package com.tsamp.sproutsocr;

import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * @author taushsampley
 */

public class LegacyCameraWrapper implements CameraWrapper, SurfaceHolder.Callback{

    private Camera camera;
    private SurfaceView surfaceView;

    private final CameraWrapper.Callback cameraWrapperCallback;

    LegacyCameraWrapper(SurfaceView view, CameraWrapper.Callback callback) {
        camera = null;
        surfaceView = view;
        surfaceView.getHolder().addCallback(this);

        cameraWrapperCallback = callback;
    }

    // ============================= CameraWrapper

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
        camera = Camera.open(id);
        camera.setPreviewDisplay(surfaceView.getHolder());
        camera.startPreview();
    }

    @Override
    public boolean cameraReady() {
        return camera != null;
    }

    @Override
    public boolean previewing() {
        return false;
    }

    @Override
    public void preview() throws Exception {

    }

    @Override
    public void capture() throws Exception {
        cameraWrapperCallback.onImageCaptured(null);
    }

//    @Override
//    public void close() throws Exception {
//        if (camera != null) {
//            camera.release();
//            camera = null;
//        }
//    }

    // ============================= SurfaceHolder.Callback

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
