package com.tsamp.sproutsocr;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.tsamp.sproutsocr.renders.ClumpRender;
import com.tsamp.sproutsocr.renders.DisplayRender;
import com.tsamp.sproutsocr.renders.AverageDifferenceRender;
import com.tsamp.sproutsocr.renders.Render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

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

    private CameraThread cameraThread;
    private GLRenderer renderer;

    public MainActivity() {
        super();
    }

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

    // ============================= Hooks

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

            surfaceView.setVisibility(View.VISIBLE);
        }
    }

    public void onProcessClicked(View sender) {
        renderer.setRenderStep(renderer.renderStep+1);
        glSurfaceView.requestRender();

        Toast.makeText(getApplicationContext(),
                "ran (" + renderer.renderStep + ") program",
                Toast.LENGTH_SHORT).show();
    }

    // ============================= FragmentActivity

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
        buttonCapture.setEnabled(false);

        cameraThread = new CameraThread();
        renderer = new GLRenderer();

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

    private class CameraThread extends Thread implements CameraWrapper.SnapshotCallback {

        private static final String TAG = "CameraThread";
        CameraWrapper camera;
        private Handler handler;

        CameraThread() {
            super("Camera Thread");
        }

        // ============================= Thread

        @Override
        public void run() {
            // prep the Looper and Handler ASAP so we can start queueing messages.
            Looper.prepare();
            handler = new Handler();

            // each constructor should use the handler they are passed to seed the loop
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera = new Camera2Wrapper(getApplicationContext(), surfaceView, handler, this);
            } else {
                camera = new LegacyCameraWrapper(surfaceView, handler, this);
            }

            // after initial camera setup, dedicate this Thread to receiving messages.
            Looper.loop();
        }

        // ============================= CameraWrapper.SnapshotCallback

        @Override
        public void onCameraOpened() {
            runOnUiThread(() -> {
                buttonCapture.setEnabled(true);
                textView.setVisibility(View.INVISIBLE);
            });
        }

        @Override
        public void onImageCaptured() {
            Log.i(TAG, "onImageCaptured");

            runOnUiThread(() -> {
                buttonCapture.setText(R.string.retake_pic);
                buttonProcess.setEnabled(true);
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
    }

    private class GLRenderer implements GLSurfaceView.Renderer {

//        private static final int STEP_DISPLAY = 0;
//        private static final int STEP_OUTLINE = 1;

        private Render[] programs;
        private int renderStep;

        private int surfaceTextureHandle;
        private Render.CompilationResources compilationResources;

        GLRenderer() {
            programs = new Render[]{
                    new DisplayRender(),
                    new AverageDifferenceRender(0, 1),
                    new ClumpRender(1, 2)
            };
            renderStep = 0;
        }

        void setRenderStep(int renderStep) {
            this.renderStep = renderStep % programs.length;
        }

        // ============================= GLSurfaceView.Renderer

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.i(TAG, "renderer surface created");
            int[] result = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_IMAGE_UNITS, result, 0);
            Log.i(TAG, "max units: " + result[0]);

            // SurfaceTexture that all Render objects can use if they choose
            surfaceTextureHandle = cameraThread.camera.createSurfaceTexture(0);
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
}
