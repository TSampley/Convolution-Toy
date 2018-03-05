package com.tsamp.sproutsocr;

import android.graphics.SurfaceTexture;

/**
 * @author taushsampley
 */

public interface CameraWrapper {
    int createSurfaceTexture(int imageUnit);
    boolean canOpen();
//    String[] cameraList();
    void open(int id) throws Exception;
    boolean cameraReady();
    void preview() throws Exception;
    boolean previewing();
    void capture() throws Exception;
    SurfaceTexture getSurfaceTexture();
    void close() throws Exception;

    interface SnapshotCallback {
        void onImageCaptured();
    }
}
