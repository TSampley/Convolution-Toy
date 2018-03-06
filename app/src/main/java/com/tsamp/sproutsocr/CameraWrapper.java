package com.tsamp.sproutsocr;

import android.graphics.SurfaceTexture;

/**
 * @author taushsampley
 */
public interface CameraWrapper {
    /**
     * Generates a new GLES texture object and binds it to a texture target within the image unit
     * indicated by {@code imageUnit}.
     * @param imageUnit The index of the GLES image unit
     * @return The GLES texture object
     */
    int createSurfaceTexture(int imageUnit);

    /**
     * Reports whether or not a call to {@link #open(int)} would succeed at this moment.
     * @return whether or not the device camera could be opened
     */
    boolean canOpen();
//    String[] cameraList();

    /**
     * Opens a device camera designated by {@code id}.
     * @param id The index of the device out of all available devices.
     * @throws Exception If something goes wrong, the exception will reveal all.
     */
    void open(int id) throws Exception;

    /**
     * Reports whether or not the camera is ready.
     * @return Whether or not the camera has been set up to capture or preview.
     * @see #preview()
     * @see #capture()
     */
    boolean cameraReady();

    /**
     * Starts previewing the device opened by {@link #open(int)}.
     * @throws Exception If something goes wrong, the exception will reveal all.
     */
    void preview() throws Exception;

    /**
     * Reports whether or not the opened device is currently previewing.
     * @return Whether or not the opened device is currently previewing.
     */
    boolean previewing();

    /**
     *
     * @throws Exception If something goes wrong, the exception will reveal all.
     */
    void capture() throws Exception;

    /**
     * Returns the {@link SurfaceTexture} object created in the most recent call to
     * {@link #createSurfaceTexture(int)}.
     * @return The {@code SurfaceTexture} most recently created, or null if never created.
     */
    SurfaceTexture getSurfaceTexture();

    /**
     * Closes the most recently opened device camera.
     * @throws Exception If something goes wrong, the exception will reveal all.
     */
    void close() throws Exception;

    /**
     * A trivial interface. A class that implements {@code CameraWrapper} should
     */
    interface SnapshotCallback {
        void onCameraOpened();
        void onImageCaptured();
        void onCameraClosed();
    }
}
