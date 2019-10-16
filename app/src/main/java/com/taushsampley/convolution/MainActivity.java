package com.taushsampley.convolution;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.taushsampley.convolution.renders.DisplayRender;
import com.taushsampley.convolution.renders.EdgeRender;
import com.taushsampley.convolution.renders.LumaRender;
import com.taushsampley.convolution.renders.Render;
import com.taushsampley.convolution.renders.TextureRender;
import com.taushsampley.convolution.util.Camera2Wrapper;
import com.taushsampley.convolution.util.CameraWrapper;
import com.taushsampley.convolution.util.LegacyCameraWrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.PermissionChecker;

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

    // taken from the Toast source file in order to enforce @Duration type
    @IntDef({Toast.LENGTH_SHORT, Toast.LENGTH_LONG})
    @Retention(RetentionPolicy.SOURCE)
    @interface Duration {}

    // region Constants

    private static final String TAG = "MainActivity";

    private static final String DIRECTORY_IMAGES = "images";
    private static final String FILE_IMAGE_PREFIX = "processedImage_";

    private static final int PERMISSION_REQUEST_CAMERA = 1;

    // endregion

    // region UI
    private SurfaceView surfaceView;
    private GLSurfaceView glSurfaceView;
    private TextView textView;
    private Button buttonCapture;
    private Button buttonProcess;
    private Button buttonSave;

    private Toast currentToast;
    // endregion

    // region Threads
    private CameraThread cameraThread;
    private GLRenderer renderer;
    // endregion

    public MainActivity() {
        super();
    }

    // region private

    private synchronized void presentToast(@StringRes int id, @Duration int duration) {
        Toast newToast = Toast.makeText(getApplicationContext(), id, duration);
        if (currentToast != null) {
            currentToast.cancel();
        }
        if (newToast != null) {
            newToast.show();
        }
        currentToast = newToast;
    }

    private synchronized void presentToast(String message, @Duration int duration) {
        Toast newToast = Toast.makeText(getApplicationContext(), message, duration);
        if (currentToast != null) {
            currentToast.cancel();
        }
        if (newToast != null) {
            newToast.show();
        }
        currentToast = newToast;
    }

    private void saveBitmap(Bitmap bitmap) {
        File directory = getFilesDir();
        File album = new File(directory, DIRECTORY_IMAGES);

        if (album.exists() || album.mkdirs()) {
            Log.i(TAG, "album exists: " + album.exists());

            File[] existingFiles = album.listFiles();
            for (File file : existingFiles) {
                if (file.getName().startsWith(FILE_IMAGE_PREFIX)) {
                    if (file.delete()) {
                        Log.i(TAG, "delete success: " + file.getName());
                    } else {
                        Log.i(TAG, "delete failure: " + file.getName());
                    }
                }
            }

            presentToast(R.string.save_image_progress, Toast.LENGTH_SHORT);
            cameraThread.handler.post(() -> {
                try {
                    File imageFile = new File(album,
                            FILE_IMAGE_PREFIX + System.currentTimeMillis() + ".png");
                    FileOutputStream fos = new FileOutputStream(imageFile);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();

                    presentToast(R.string.save_image_success, Toast.LENGTH_SHORT);

                    Uri contentUri = FileProvider.getUriForFile(this,
                            getPackageName(), imageFile);
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(contentUri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } else {
            presentToast("Could not acquire album handle", Toast.LENGTH_SHORT);
        }
    }

    // endregion

    // region public

    public static String readRawResource(Context context, @RawRes int id) {
        InputStream inputStream = context.getResources().openRawResource(id);
        String result = null;
        try {
            StringBuilder builder = new StringBuilder();
            byte[] bytes = new byte[Math.max(1, inputStream.available())];
            int readLength;
            while ((readLength = inputStream.read(bytes)) >= 0) {
                builder.append(new String(bytes, 0, readLength, Charset.forName("UTF-8")));
            }
            result = builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static int compileShader(int shaderType, String source) {
        int shaderHandle = GLES20.glCreateShader(shaderType);

        if (shaderHandle == 0) {
            throw new GLESShaderAllocationException();
        } else {
            GLES20.glShaderSource(shaderHandle, source);
            GLES20.glCompileShader(shaderHandle);
            int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            if (compileStatus[0] == 0) {
                String str = GLES20.glGetShaderInfoLog(shaderHandle);
                GLES20.glDeleteShader(shaderHandle);
                throw new GLESCompileException(shaderHandle, shaderType, str);
            }
        }

        return shaderHandle;
    }

    public static int compileProgram(int vertexShader, int fragmentShader) {
        int programHandle = GLES20.glCreateProgram();

        if (programHandle == 0) {
            throw new GLESProgramAllocationException();
        } else {
            GLES20.glAttachShader(programHandle, vertexShader);
            GLES20.glAttachShader(programHandle, fragmentShader);

            GLES20.glLinkProgram(programHandle);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] == 0) {
                String str = GLES20.glGetProgramInfoLog(programHandle);
                GLES20.glDeleteProgram(programHandle);
                throw new GLESProgramLinkException(programHandle, vertexShader, fragmentShader, str);
            }
        }

        return programHandle;
    }

    // region Callbacks

    public void onCaptureClicked(View sender) {
        if (cameraThread.camera.previewing()) {    // capture photo
            Log.i(TAG, "SNAPSHOT REQUESTED");
            renderer.setRenderStep(0);
            try {
                cameraThread.camera.capture();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {                                    // resume preview
            Log.i(TAG, "CONTINUE PREVIEW");
            buttonCapture.setText(R.string.capture_pic);
            buttonProcess.setEnabled(false);
            buttonSave.setEnabled(false);

            surfaceView.setVisibility(View.VISIBLE);
        }
    }

    public void onProcessClicked(View sender) {
        renderer.setRenderStep(renderer.renderStep+1);
        glSurfaceView.requestRender();

        presentToast("ran (" + renderer.renderStep + ") program",
                Toast.LENGTH_SHORT);
    }

    public void onSaveClicked(View sender) {
        glSurfaceView.queueEvent(renderer::downloadImage);
    }

    // endregion
    // endregion public

    // region FragmentActivity Callbacks

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            throw new IllegalStateException("Unable to acquire ActivityManager");
        }
        ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
        if (configInfo == null) {
            throw new IllegalStateException("Unable to acquire ConfigurationInfo from ActivityManager");
        }
        if (configInfo.reqGlEsVersion < 0x00020000) {
            // upper 16 bits: major version; lower 16 bits: minor version
            throw new IllegalStateException("OpenGL ES 2.0 required");
        }

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        glSurfaceView = findViewById(R.id.glView);
        textView = findViewById(R.id.textView);
        buttonCapture = findViewById(R.id.button_capture);
        buttonProcess = findViewById(R.id.button_process);
        buttonSave = findViewById(R.id.button_save);
        buttonCapture.setEnabled(false);
        buttonSave.setEnabled(false);

        cameraThread = new CameraThread();
        renderer = new GLRenderer();

        currentToast = null;

        // setup the GLSurfaceView to start rendering
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setEGLConfigChooser(false);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // check permissions
        int grant = PermissionChecker.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA);
        switch (grant) {
            case PermissionChecker.PERMISSION_GRANTED: // start camera stuffs
                if (!cameraThread.isAlive()) {
                    cameraThread.start();
                }
                break;
            case PermissionChecker.PERMISSION_DENIED_APP_OP: // user has disabled permission -> request
            case PermissionChecker.PERMISSION_DENIED: // permission not yet granted -> request
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
                break;
        }

        if (!cameraThread.isAlive()) {
            cameraThread.start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();

        try {
            cameraThread.run();
        } catch (Exception e) {e.printStackTrace();}
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();

        try {
            cameraThread.camera.close();
        } catch (Exception e) {e.printStackTrace();}
    }

    @Override
    protected void onStop() {
        super.onStop();

        cameraThread.handler.post(() -> {
            Looper looper = Looper.myLooper();
            if (looper != null) {
                looper.quit();
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
                    if (!cameraThread.isAlive()) {
                        cameraThread.start();
                    }
                } else {
                    finish();
                }
                break;
            default:
                throw new IllegalStateException();
        }
    }

    // endregion

    private class CameraThread extends Thread implements CameraWrapper.SnapshotCallback {

        private static final String TAG = "CameraThread";
        CameraWrapper camera;
        private Handler handler;

        CameraThread() {
            super("Camera Thread");
        }

        // region public

        CameraWrapper getCamera(Handler handler) {
            CameraWrapper camera;
            // each constructor should use the handler they are passed to seed the loop
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera = new Camera2Wrapper(getApplicationContext(), surfaceView, handler, this);
            } else {
                camera = new LegacyCameraWrapper(surfaceView, handler, this);
            }
            return camera;
        }

        // endregion

        // region Runnable

        @Override
        public void run() {
            // prep the Looper and Handler ASAP so we can start queueing messages.
            Looper.prepare();
            handler = new Handler();

            camera = getCamera(handler);

            // after initial camera setup, dedicate this Thread to receiving messages.
            Looper.loop();
        }

        // endregion

        // region CameraWrapper.SnapshotCallback

        @Override
        public void onCameraOpened() {
            runOnUiThread(() -> {
                buttonCapture.setEnabled(true);
                textView.setVisibility(View.INVISIBLE);
            });
        }

        @Override
        public void onImageCaptured(ByteBuffer imageData, int width, int height) {
            Log.i(TAG, "onImageCaptured");

            renderer.uploadImage(imageData, width, height);
            runOnUiThread(() -> {
                buttonCapture.setText(R.string.retake_pic);
                buttonProcess.setEnabled(true);
                buttonSave.setEnabled(true);
                surfaceView.setVisibility(View.INVISIBLE);
                glSurfaceView.requestRender();
            });
            Toast msg = Toast.makeText(getApplicationContext(),
                    "Captured", Toast.LENGTH_SHORT);
            msg.show();
        }

        @Override
        public void onCameraClosed() {
            runOnUiThread(() -> {
                buttonCapture.setText(R.string.capture_pic);
                buttonCapture.setEnabled(false);
                textView.setVisibility(View.VISIBLE);
            });
        }

        // endregion
    }

    private class GLRenderer implements GLSurfaceView.Renderer {

//        private static final int STEP_DISPLAY = 0;
//        private static final int STEP_OUTLINE = 1;

        private Render[] programs;
        private TextureRender edgeProgram;
        private int renderStep;

        private int surfaceTextureHandle;
        private int lumaTextureHandle;
        private Render.CompilationResources compilationResources;

        private ByteBuffer imageData;
        private int width;
        private int height;

        GLRenderer() {
            programs = new Render[]{
                    new DisplayRender(),
//                    new AverageDifferenceRender(0, 1),
//                    new ClumpRender(1, 2),
                    new LumaRender(0, 3),
                    edgeProgram = new EdgeRender(3, 4)
            };
            renderStep = 0;

            imageData = null;
        }

        void setRenderStep(int renderStep) {
            this.renderStep = renderStep % programs.length;
        }

        synchronized void uploadImage(ByteBuffer imageData, int width, int height) {
            this.imageData = imageData;
            this.width = width;
            this.height = height;

            glSurfaceView.queueEvent(this::uploadImage);
        }

        synchronized void uploadImage() {
            Log.i(TAG, "upload Image called");
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lumaTextureHandle);
            // fill with initial texture data
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                    width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, imageData);
            imageData = null;
        }

        synchronized void downloadImage() {
            int width = compilationResources.getTextureWidth();
            int height = compilationResources.getTextureHeight();
            ByteBuffer imageBuffer = ByteBuffer.allocate(width*height*4);
            imageBuffer.position(0);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, edgeProgram.getTargetFramebuffer());
            GLES20.glReadPixels(0, 0, width, height,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, imageBuffer);

            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(imageBuffer);

            runOnUiThread(() -> saveBitmap(bitmap));
        }

        // ============================= GLSurfaceView.Renderer

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.i(TAG, "renderer surface created");
            int[] result = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, result, 0);
            Log.i(TAG, "max units: " + result[0]);

            int[] handles = new int[1];
            // SurfaceTexture that all Render objects can use if they choose
            surfaceTextureHandle = cameraThread.camera.createSurfaceTexture(0);
            GLES20.glGenTextures(1, handles, 0);
            lumaTextureHandle = handles[0];
            // set texture unit
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            // bind texture object to external target
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lumaTextureHandle);
            // set the target's params
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            // common shader that all Render objects can use if they choose
            String vertCode = readRawResource(getApplicationContext(), R.raw.display_vert);
            String fragCode = readRawResource(getApplicationContext(), R.raw.display_2d_frag);
            int vertShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertCode);
            int display2DHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, fragCode);
            int display2DProgram = compileProgram(vertShaderHandle, display2DHandle);

            compilationResources = new Render.CompilationResources(
                    cameraThread.camera.getTextureWidth(),
                    cameraThread.camera.getTextureHeight(),
                    vertShaderHandle, display2DProgram) {
                @Override
                public int compileShader(int shaderType, int id) {
                    return MainActivity.compileShader(shaderType,
                            readRawResource(getApplicationContext(), id));
                }

                @Override
                public int linkProgram(int vertShader, int fragShader) {
                    return MainActivity.compileProgram(vertShader, fragShader);
                }

                @Override
                public int getOutputWidth() {
                    return glSurfaceView.getWidth();
                }

                @Override
                public int getOutputHeight() {
                    return glSurfaceView.getHeight();
                }
            };
            for (Render render : programs) {
                render.compileAndLink(compilationResources);
            }

            // set the clear color
            GLES20.glClearColor(.5f, .5f, .5f, 1);
//            GLES20.glDisable(GLES20.GL_CULL_FACE);
//            GLES20.glCullFace(GLES20.GL_CCW);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.i(TAG, "renderer surface changed");
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            Log.d(TAG, "onDrawFrame");

            if (surfaceTextureHandle > 0) { // only bother drawing if the surface texture is valid
                // set active texture unit
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                // update texture to most recent image. implicitly binds to GL_TEXTURE_EXTERNAL_OES
                cameraThread.camera.getSurfaceTexture().updateTexImage();

                programs[renderStep].run(compilationResources);
            } else {
                Log.i(TAG, "GLRenderer does not have surfaceTextureHandle yet");
            }
        }
    }

    // region static classes

    private static class GLESShaderAllocationException extends RuntimeException {}

    private static class GLESCompileException extends RuntimeException {
        GLESCompileException(int shaderHandle, int shaderType, String infoLog) {
            super("shader [" + shaderHandle + ", " +shaderType+"] encountered problem: " +
                    infoLog);
        }
    }

    private static class GLESProgramAllocationException extends RuntimeException {}

    private static class GLESProgramLinkException extends RuntimeException {
        GLESProgramLinkException(int programHandle, int vertexHandle, int fragmentHandle,
                                 String infoLog) {
            super("program [" + programHandle + ": " + vertexHandle + " + " + fragmentHandle +
                    "] encountered problem: " + infoLog);
        }
    }

    // endregion
}
