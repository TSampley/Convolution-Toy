package com.tsamp.sproutsocr;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;

/**
 * <ol>
 *     <li>Get {@link CameraManager} from passed in {@code Context}.</li>
 *     <li>Retrieve {@link Surface} from passed in {@link SurfaceView} objects.</li>
 *     <li>On {@link #open(int)}, attempt to retrieve the {@link CameraDevice}.
 *     <ol>
 *         <li>Get {@code CameraDevice} from
 *         {@link CameraManager#openCamera(String, CameraDevice.StateCallback, Handler)}.</li>
 *         <li>Once {@link #onOpened(CameraDevice)} is called, create a new capture session.</li>
 *         <li>Once {@link CaptureStateCallback#onConfigured(CameraCaptureSession)} is called,
 *         create a {@link CaptureRequest}.</li>
 *     </ol>
 *     </li>
 * </ol>
 * @author taushsampley
 */

@TargetApi(21)
public class Camera2Wrapper extends CameraDevice.StateCallback implements CameraWrapper {

    private static final String TAG = "Camera2Wrapper";
    /*
    CP: camera permission
    Srf: surface created
    Man: manager usable
    Cam: camera usable
    Pre: preview surface
    Pro: process surface
    Cap: camera capture session
    PR: preview request
    SR: single request
    Ses: session configured

    CP              -> Man
    Man             -> Pro ^ Cam
    Srf             -> Pre
    Cam ^ Pre       -> PR
    Cam ^ Pre ^ Pro -> SR
    PR ^ SR         -> Ses
     */

    // manager is available when we know we have camera permission
    private CameraManager manager;
    // camera is available when #onOpened(CameraDevice) is called
    private CameraDevice camera;
    // previewSurface is available when SurfaceCallback#surfaceCreated(SurfaceHolder) is called
    private Surface previewSurface;
    // processSurface is available when a camera is selected to open
    private Surface processSurface;
    // captureSession is available after the camera and both surfaces are available
    private CameraCaptureSession captureSession;

    // previewRequest is available once camera and previewSurface is available
    private CaptureRequest previewRequest;
    // singleRequest is available once camera, processSurface, and previewSurface is available
    private CaptureRequest singleRequest;

    private ImageReader imageReader;
    private final CaptureStateCallback captureStateCallback;
    private final CameraCaptureCallback previewCaptureCallback;
    private final CameraCaptureCallback snapshotCaptureCallback;

    private final Handler callbackHandler;

    Camera2Wrapper(@NonNull Context context, @NonNull SurfaceView surfaceView,
                   @NonNull CameraWrapper.Callback callback) {
        manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        camera = null;
        previewSurface = null;
        processSurface = null;
        captureSession = null;
        previewRequest = null;
        singleRequest = null;

        surfaceView.getHolder().addCallback(new SurfaceCallback());

        imageReader = null;
        captureStateCallback = new CaptureStateCallback();
        previewCaptureCallback = new CameraCaptureCallback(0, null);
        snapshotCaptureCallback = new CameraCaptureCallback(1, callback);

        callbackHandler = new Handler(Looper.getMainLooper());
    }

    // Only called when camera, processSurface, and previewSurface are all available
    private void configSession() {
        boolean surfaceViewLoaded = previewSurface != null;
        boolean cameraCalledToOpen = processSurface != null;
        boolean cameraAcquired = camera != null;

        if (surfaceViewLoaded && cameraCalledToOpen && cameraAcquired) {
            Log.i(TAG, "camera ^ previewSurface ^ processSurface");
            try {
                Log.i(TAG, "building previewRequest");
                // request for previewing camera
                CaptureRequest.Builder builder =
                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera.getId());
                Range<Integer>[] availableRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                if (availableRanges != null && availableRanges.length > 0) {
                    Range<Integer> lowest = availableRanges[0];
                    for (int i = 1; i < availableRanges.length; i++) {
                        Range<Integer> range = availableRanges[i];
                        if (range.getLower() < lowest.getLower() ||
                                (range.getLower().equals(lowest.getLower()) &&
                                        (range.getUpper() < lowest.getUpper()))) {
                            lowest = range;
                        }
                    }
                    lowest = new Range<>(10, 10);
                    builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, lowest);
                    Log.i(TAG, "frame rate set to " + lowest.getLower());
                }
                builder.addTarget(previewSurface);
                previewRequest = builder.build();

                Log.i(TAG, "building singleRequest");
                // request for taking a picture
                builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
                builder.addTarget(processSurface);
                builder.addTarget(previewSurface);
                singleRequest = builder.build();

                Log.i(TAG, "creating capture session");
                ArrayList<Surface> surfaces = new ArrayList<>(1);
                surfaces.add(previewSurface);
                surfaces.add(processSurface);
                camera.createCaptureSession(surfaces, captureStateCallback, callbackHandler);
                // result will be in #onConfigured(CameraCaptureSession) or
                // #onConfigureFailed(CameraCapture Session)
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    // ============================= CameraDevice.StateCallback

    @Override
    public void onOpened(@NonNull CameraDevice camera) {
        Log.i(TAG, "camera opened");
        this.camera = camera;

        configSession();
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {
        Log.i(TAG, "disconnected");
    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {
        Log.i(TAG, "error");
    }

    // ============================= CameraWrapper

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
        try {
            String stringId = manager.getCameraIdList()[id];
            Log.i(TAG, "requested open(" + id + ":" + stringId + ")");

            // processSurface can be created as soon as we know which Camera is requested
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(stringId);
            Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Size size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            if (rect == null) {
                rect = new Rect(0, 0, 0, 0);
            }
            if (size == null) {
                size = new Size(0, 0);
            }
            imageReader = ImageReader.newInstance(rect.width(), rect.height(),
                    ImageFormat.YUV_420_888, 2);
            processSurface = imageReader.getSurface();
            Log.i(TAG, "ACTIVE PIXELS " + rect.toShortString() + " within " + size.toString());

            manager.openCamera(stringId, this, callbackHandler);
            // result will be found in #onOpened(CameraDevice)
            Log.i(TAG, "opening camera");
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean cameraReady() {
        return captureSession != null;
    }

    @Override
    public boolean previewing() {
        return previewCaptureCallback.getActive();
    }

    @Override
    public void preview() throws Exception {
        captureSession.setRepeatingRequest(previewRequest, previewCaptureCallback, callbackHandler);
    }

    @Override
    public void capture() throws Exception {
        captureSession.abortCaptures();
        captureSession.capture(singleRequest, snapshotCaptureCallback, callbackHandler);
    }

//    @Override
//    public void close() throws Exception {
//        if (camera != null) {
//            camera.close();
//            camera = null;
//        }
//    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surface ready");
            previewSurface = holder.getSurface();

            configSession();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surface changed");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    }

    private class CaptureStateCallback extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "session configured");
            captureSession = session;

            try {
                Log.i(TAG, "request capture");
                preview();
                Log.i(TAG, "capture requested");
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

    private class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {

        private boolean active;
        private final int id;
        private final CameraWrapper.Callback callback;

        CameraCaptureCallback(int id, CameraWrapper.Callback callback) {
            active = false;
            this.id = id;
            this.callback = callback;
        }

        boolean getActive() {
            return active;
        }

        // ============================= CameraCaptureSession.CaptureCallback

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            Log.i(TAG, "capture("+id+") started");
            active = true;
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            Log.i(TAG, "capture("+id+") progressed");
            // middle of a capture
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            Log.i(TAG, "capture("+id+") completed");
            // a single capture has completed. if this is a repeating request, there could be more
            // captures coming. #onCaptureSequenceCompleted is called when it's completely done.
            if (callback != null) {
                imageReader.close();
                callback.onImageCaptured(imageReader.acquireLatestImage());
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.i(TAG, "capture("+id+") failed");
            active = false;
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            Log.i(TAG, "capture("+id+") sequence completed");
            active = false;
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            Log.i(TAG, "capture("+id+") sequence aborted");
            active = false;
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            Log.i(TAG, "capture("+id+") buffer lost");
            // doesn't mean the whole capture failed -> capturing is still occurring
        }
    }
}
