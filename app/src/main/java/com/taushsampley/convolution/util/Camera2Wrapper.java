package com.taushsampley.convolution.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * <ol>
 *     <li>Get {@link CameraManager} from passed in {@code Context}.</li>
 *     <li>Register callbacks on passed in {@link SurfaceView} to retrieve {@link Surface} once it's
 *     created.</li>
 *     <li>On {@link #open(int)}, attempt to retrieve the {@link CameraDevice}.
 *     <ol>
 *         <li>Call {@link CameraManager#openCamera(String, CameraDevice.StateCallback, Handler)}.</li>
 *         <li>Once {@link CameraStateCallback#onOpened(CameraDevice)} is called, save the opened
 *         camera instance. </li>
 *         <li>Create a new capture session.</li>
 *         <li>Once {@link SessionStateCallback#onConfigured(CameraCaptureSession)} is called,
 *         create a {@link CaptureRequest}.</li>
 *     </ol>
 *     </li>
 * </ol>
 * @author taushsampley
 */

@TargetApi(21)
public class Camera2Wrapper implements CameraWrapper {

    private static final String TAG = "Camera2Wrapper";
    /*
    CP: activeCamera permission
    Srf: surface created
    Man: manager usable
    Cam: activeCamera usable
    Pre: preview surface
    Pro: process surface
    Cap: activeCamera capture session
    PR: preview request
    SR: single request
    Ses: session configured

    Pro
    CP              -> Man
    Man             -> Cam
    Srf             -> Pre
    Cam ^ Pre       -> PR
    Cam ^ Pre ^ Pro -> SR
    PR ^ SR         -> Ses
     */

    // manager is available when we know we have activeCamera permission
    private CameraManager manager;
    private CameraCharacteristics characteristics;
    private StreamConfigurationMap streamConfigurationMap;
    // activeCamera is available when #onOpened(CameraDevice) is called
    private CameraDevice activeCamera;

    // previewSurface is available when SurfaceCallback#surfaceCreated(SurfaceHolder) is called
    private Surface previewSurface;
    // colorSurface is available when a activeCamera is selected to open
    private Surface colorSurface;
    private Surface luminanceSurface;

    // previewRequest is available once activeCamera and previewSurface is available
    private CaptureRequest previewRequest;
    // singleRequest is available once activeCamera, colorSurface, and previewSurface is available
    private CaptureRequest singleRequest;

    // captureSession is available after the activeCamera and both surfaces are available
    private CameraCaptureSession captureSession;

    private Size surfaceTextureSize;
    private SurfaceTexture surfaceTexture;
    private ImageReader imageReader;
    private final CameraStateCallback cameraStateCallback;
    private final SessionStateCallback sessionStateCallback;
    private final SessionCaptureCallback previewCaptureCallback;
    private final SessionCaptureCallback snapshotCaptureCallback;

    private final Handler cameraHandler;

    public Camera2Wrapper(@NonNull Context context, @NonNull SurfaceView surfaceView,
                          @NonNull Handler cameraHandler, @NonNull SnapshotCallback snapshotCallback) {
        manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        characteristics = null;
        streamConfigurationMap = null;
        activeCamera = null;
        previewSurface = null;
        colorSurface = null;
        luminanceSurface = null;
        captureSession = null;
        previewRequest = null;
        singleRequest = null;

        surfaceTextureSize = null;
        surfaceTexture = null;
        imageReader = null;
        cameraStateCallback = new CameraStateCallback();
        sessionStateCallback = new SessionStateCallback();
        previewCaptureCallback = new SessionCaptureCallback(0, null);
        previewCaptureCallback.setLogLevel(Log.VERBOSE);
        snapshotCaptureCallback = new SessionCaptureCallback(1, snapshotCallback);
        snapshotCaptureCallback.setLogLevel(Log.DEBUG);

        this.cameraHandler = cameraHandler;

        surfaceView.getHolder().addCallback(new SurfaceCallback());
    }

    private void checkForOpen() {
        if (canOpen()) {
            try {
                open(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void buildRequests() throws CameraAccessException {
        Log.i(TAG, "building previewRequest");

        if (streamConfigurationMap == null) {
            throw new IllegalStateException("SCALAR_STREAM_CONFIGURATION_MAP is null " +
                    "in CameraCharacteristics");
        } else {
            Range<Integer> aeTargetFPSRange = new Range<>(15, 15);

            // set Auto Exposure FPS Range
            Range<Integer>[] availableRanges = characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (availableRanges != null) {
                // just choose the first one
                aeTargetFPSRange = availableRanges[0];
            }

            // request for previewing activeCamera
            CaptureRequest.Builder builder =
                    activeCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, aeTargetFPSRange);
            builder.addTarget(previewSurface);
            previewRequest = builder.build();

            // request for taking a picture and processing it
            builder = activeCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(colorSurface);
            builder.addTarget(luminanceSurface);
//        builder.addTarget(previewSurface);
            singleRequest = builder.build();
        }
    }

    // ============================= CameraWrapper

    @Override
    public int createSurfaceTexture(int imageUnit) {
        Log.i(TAG, "creating surface texture in context of " + Thread.currentThread());
        // generate texture object
        int[] texHandles = new int[1];
        GLES20.glGenTextures(1, texHandles, 0);
        int textureHandle = texHandles[0];
        if (textureHandle > 0) { // 0 is reserved, so it will never be generated
            try {
                // for now assume camera 0
                String stringId = manager.getCameraIdList()[0];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(stringId);
                StreamConfigurationMap streamConfigurationMap =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (streamConfigurationMap == null) {
                    throw new IllegalStateException();
                }
                Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888);
                if (sizes == null) {
                    throw new IllegalStateException();
                }
                surfaceTextureSize = sizes[0];
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            // set texture unit
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + imageUnit);
            // bind texture object to external target
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureHandle);
            // set the target's params
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            surfaceTexture = new SurfaceTexture(textureHandle, false);
            surfaceTexture.setDefaultBufferSize(
                    surfaceTextureSize.getWidth(),
                    surfaceTextureSize.getHeight());
            imageReader = ImageReader.newInstance(
                    surfaceTextureSize.getWidth(), surfaceTextureSize.getHeight(),
                    ImageFormat.YUV_420_888, 1);

            colorSurface = new Surface(surfaceTexture);
            luminanceSurface = imageReader.getSurface();
        } else {
            throw new IllegalStateException("Unable to acquire texture handle.");
        }

        // #createSurfaceTexture will only work if called from a Thread with a GL context, so this
        // has to be delegated to our camera handler
        cameraHandler.post(this::checkForOpen);

        return textureHandle;
    }

    @Override
    public boolean canOpen() {
        return previewSurface != null && colorSurface != null;
    }

//    @Override
//    public String[] cameraList() {
//        String[] list;
//        try {
//            list = manager.getCameraIdList();
//        } catch (CameraAccessException e) {
//            list = new String[0];
//        }
//        return list;
//    }

    @Override
    public void open(int id) throws Exception {
        if (!canOpen()) {
            throw new IllegalStateException("Can't call #open(int) while #canOpen() returns false.");
        }
        try {
            String stringId = manager.getCameraIdList()[id];
            Log.i(TAG, "requested open(" + id + ":" + stringId + ")");

            characteristics = manager.getCameraCharacteristics(stringId);
            streamConfigurationMap =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            manager.openCamera(stringId, cameraStateCallback, cameraHandler);
            // result will be found in CameraStateCallback#onOpened(CameraDevice)
            Log.i(TAG, "opening activeCamera");
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void preview() throws Exception {
        Log.i(TAG, "session preview request");
        captureSession.setRepeatingRequest(previewRequest, previewCaptureCallback, cameraHandler);
        // results will appear in methods of SessionCaptureCallback
    }

    @Override
    public boolean previewing() {
        return previewCaptureCallback.getActive();
    }

    @Override
    public void capture() throws Exception {
        Log.i(TAG, "capture requested");
        captureSession.stopRepeating();
        captureSession.capture(singleRequest, snapshotCaptureCallback, cameraHandler);
        // results will appear in methods of SessionCaptureCallback
    }

    @Override
    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    @Override
    public int getTextureWidth() {
        return surfaceTextureSize == null ? 0 : surfaceTextureSize.getWidth();
    }

    @Override
    public int getTextureHeight() {
        return surfaceTextureSize == null ? 0 : surfaceTextureSize.getHeight();
    }

    @Override
    public void close() throws Exception {
        Log.i(TAG, "close activeCamera requested");
        if (activeCamera == null) {
            Log.i(TAG, "no known activeCamera already open");
        } else {
            // keep reference to manager
            // close and release activeCamera
            activeCamera.close();
            activeCamera = null;

            // close and release surfaces if they were ever acquired
            if (previewSurface != null) {
                previewSurface.release();
                previewSurface = null;
            }
            if (colorSurface != null) {
                colorSurface.release();
                colorSurface = null;
            }
            if (luminanceSurface != null) {
                luminanceSurface.release();
                luminanceSurface = null;
            }

            // release requests
            previewRequest = null;
            singleRequest = null;

            // release session - if there was an activeCamera, session will be closed as a result
            // of activeCamera being closed
            captureSession = null;

            // release surfaceTexture if it was ever created
            if (surfaceTexture != null) {
                surfaceTexture.release();
                surfaceTexture = null;
            }

            // hold on to Callback instances in case this Wrapper gets re-opened

            // results will appear in methods of SessionStateCallback
            snapshotCaptureCallback.snapshotCallback.onCameraClosed();
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surface created");
            previewSurface = holder.getSurface();
            checkForOpen();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surface changed");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surface destroyed");

            previewSurface.release();
            previewSurface = null;
        }
    }

    private class CameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.i(TAG, "activeCamera opened");
            activeCamera = camera;
            snapshotCaptureCallback.snapshotCallback.onCameraOpened();

            try {
                buildRequests();

                Log.i(TAG, "creating capture session");
                activeCamera.createCaptureSession(
                        Arrays.asList(previewSurface, colorSurface, luminanceSurface),
                        sessionStateCallback, cameraHandler);
                // result will be in SessionStateCallback#onConfigured(CameraCaptureSession) or
                // SessionStateCallback#onConfigureFailed(CameraCapture Session)
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i(TAG, "disconnected");

            if (activeCamera != null) {
                activeCamera.close();
                activeCamera = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.i(TAG, "error");
        }
    }

    private class SessionStateCallback extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "session configured");
            captureSession = session;

            try {
                Log.i(TAG, "request preview");
                preview();
                Log.i(TAG, "preview requested");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "configure failure");
        }

        @Override
        public void onReady(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "session ready");
        }

        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "session active");
        }

        @Override
        public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "session capture queue empty");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "session closed");
        }

        @Override
        public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
            Log.i(TAG, "session surface prepared");
        }
    }

    private class SessionCaptureCallback extends CameraCaptureSession.CaptureCallback {

        private boolean active;
        private final int id;
        private int logLevel;
        private final SnapshotCallback snapshotCallback;

        SessionCaptureCallback(int id, SnapshotCallback snapshotCallback) {
            active = false;
            this.id = id;
            this.snapshotCallback = snapshotCallback;
        }

        boolean getActive() {
            return active;
        }

        void setLogLevel(int logLevel) {
            this.logLevel = logLevel;
        }

        // ============================= CameraCaptureSession.CaptureCallback

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            Log.println(logLevel, TAG, "capture("+id+") started");
            active = true;
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            Log.println(logLevel, TAG, "capture("+id+") progressed");
            // middle of a capture
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            Log.println(logLevel, TAG, "capture("+id+") completed");
            // a single capture has completed. if this is a repeating request, there could be more
            // captures coming. #onCaptureSequenceCompleted is called when it's completely done.
            if (snapshotCallback != null) {
                Image latestImage = imageReader.acquireLatestImage();
                Image.Plane[] planes = latestImage.getPlanes();
                Image.Plane luminance = planes[0];
                ByteBuffer buffer = luminance.getBuffer();
                ByteBuffer copy = ByteBuffer.allocate(buffer.capacity());
                copy.put(buffer);
                copy.position(0);

                latestImage.close();

                snapshotCallback.onImageCaptured(copy,
                        imageReader.getWidth(), imageReader.getHeight());
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.println(logLevel, TAG, "capture("+id+") failed");
            active = false;
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            Log.println(logLevel, TAG, "capture("+id+") sequence completed");
            active = false;
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            Log.println(logLevel, TAG, "capture("+id+") sequence aborted");
            active = false;
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            Log.println(logLevel, TAG, "capture("+id+") buffer lost");
            // doesn't mean the whole capture failed -> capturing is still occurring
        }
    }
}
