package com.tsamp.sproutsocr;

import android.graphics.Bitmap;

/**
 * @author taushsampley
 */

public interface CameraWrapper {
//    String[] cameraList();
    void open(int id) throws Exception;
    boolean cameraReady();
    boolean previewing();
    void preview() throws Exception;
    void capture() throws Exception;
    void close() throws Exception;

    interface Callback {
        void onImageCaptured(Bitmap capturedImage);
    }
}
