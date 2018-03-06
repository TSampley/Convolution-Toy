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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
        try {
            if (cameraThread.camera.previewing()) {    // capture photo
                Log.i(TAG, "SNAPSHOT REQUESTED");
                cameraThread.camera.capture();
            } else {                                    // resume preview
                Log.i(TAG, "CONTINUE PREVIEW");
                buttonCapture.setText(R.string.capture_pic);
                buttonProcess.setEnabled(false);

                cameraThread.camera.preview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onProcessClicked(View sender) {
        glSurfaceView.requestRender();
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
        GLRenderer renderer = new GLRenderer();

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

        private static final int STEP_DISPLAY = 0;
        private static final int STEP_OUTLINE = 1;

        private final int BYTES_PER_FLOAT = 4;

        // render info
        private int positionIndex;
        private int textureIndex;
        private int textureCoordIndex;
        private int radiusIndex;

        private int textureHandle;
        private int displayProgram;
        private int edgeProgram;

        // "geometry" info
        private final int vertexCount = 4;
        float[] vertices;
        private final int vertexOffset = 0;
        private final int vertexSize = 2;

        float[] texCoords;
        private final int texOffset = vertexSize*vertexCount;
        private final int texSize = 2;

        private final int stride = 0;
        private final FloatBuffer rectVertices;

        // process steps
        private int renderStep;

        GLRenderer() {
            // positionIndex, textureIndex, textureCoordIndex, textureHandle, displayProgram
            // will be determined later

            float left = -1;
            float right = 1;
            float top = 1;
            float bottom = -1;
            vertices = new float[]{
                    left, top,
                    right, top,
                    left, bottom,
                    right, bottom
            };
            texCoords = new float[]{
                    0, 0,
                    1, 0,
                    0, 1,
                    1, 1
            };
            rectVertices = ByteBuffer.allocateDirect(
                    vertexCount*(vertexSize + texSize)*BYTES_PER_FLOAT)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            rectVertices.put(vertices, 0, vertexCount*vertexSize);
            rectVertices.put(texCoords, 0, vertexCount*texSize);

            renderStep = STEP_DISPLAY;
        }

        private int compileShader(int shaderType, String source) {
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

        private int compileProgram(int vertexShader, int fragmentShader) {
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

        // ============================= GLSurfaceView.Renderer

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.i(TAG, "renderer surface created");

            // retrieve code from files
            String vertCode = readRawResource(getApplicationContext(), R.raw.display_vert);
            String displayFragCode = readRawResource(getApplicationContext(), R.raw.display_frag);
            String edgesFragCode = readRawResource(getApplicationContext(), R.raw.edges_frag);
            // compile shaders
            int vertShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertCode);
            int fragShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, displayFragCode);
            int edgesFragHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, edgesFragCode);
            // link program
            displayProgram = compileProgram(vertShaderHandle, fragShaderHandle);
            edgeProgram = compileProgram(vertShaderHandle, edgesFragHandle);

            // retrieve program attribute and uniform locations
            positionIndex = GLES20.glGetAttribLocation(displayProgram, "a_position");
            textureIndex = GLES20.glGetUniformLocation(displayProgram, "u_texture");
            textureCoordIndex = GLES20.glGetAttribLocation(displayProgram, "a_texCoord");
            radiusIndex = GLES20.glGetUniformLocation(edgeProgram, "u_radius");

            textureHandle = cameraThread.camera.createSurfaceTexture(0);

            int[] viewport = new int[4];
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
            int[] maxView = new int[2];
            GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, maxView, 0);

            GLES20.glClearColor(.5f, .5f, .5f, 1);
//            GLES20.glDisable(GLES20.GL_CULL_FACE);
//            GLES20.glCullFace(GLES20.GL_CCW);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.i(TAG, "renderer surface changed");
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            Log.d(TAG, "onDrawFrame");

            // pre-render cleanup
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            if (textureHandle >= 0) {
                int program = renderStep == STEP_DISPLAY ? displayProgram : edgeProgram;
                // let's begin
                GLES20.glUseProgram(program);

                rectVertices.position(vertexOffset);
                GLES20.glVertexAttribPointer(positionIndex, vertexSize, GLES20.GL_FLOAT,
                        false, stride, rectVertices);
                GLES20.glEnableVertexAttribArray(positionIndex);

                rectVertices.position(texOffset);
                GLES20.glVertexAttribPointer(textureCoordIndex, texSize, GLES20.GL_FLOAT,
                        false, stride, rectVertices);
                GLES20.glEnableVertexAttribArray(textureCoordIndex);

                if (renderStep == STEP_OUTLINE) {
                    float rx = 1.0f / cameraThread.camera.getTextureWidth();
                    float ry = 1.0f / cameraThread.camera.getTextureHeight();
                    Log.i(TAG, "radius used [" + rx + ", " + ry + "]");
                    GLES20.glUniform2f(radiusIndex, rx, ry);
                }

                // set active texture unit
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                // update texture to most recent image. implicitly binds to GL_TEXTURE_EXTERNAL_OES
                cameraThread.camera.getSurfaceTexture().updateTexImage();
                // tell sampler identified by `textureIndex` to use texture unit 0
                GLES20.glUniform1i(textureIndex, 0);

                // run program
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        "ran (" + renderStep + ") program",
                        Toast.LENGTH_SHORT).show());
                renderStep = (renderStep + 1) % 2;
            } else {
                Log.i(TAG, "GLRenderer does not have textureHandle yet");
            }
        }

        // ============================= Nested Classes

        private class GLESShaderAllocationException extends RuntimeException {}

        private class GLESCompileException extends RuntimeException {
            GLESCompileException(int shaderHandle, int shaderType, String infoLog) {
                super("shader [" + shaderHandle + ", " +shaderType+"] encountered problem: " +
                        infoLog);
            }
        }

        private class GLESProgramAllocationException extends RuntimeException {}

        private class GLESProgramLinkException extends RuntimeException {
            GLESProgramLinkException(int programHandle, int vertexHandle, int fragmentHandle,
                                     String infoLog) {
                super("program [" + programHandle + ": " + vertexHandle + " + " + fragmentHandle +
                        "] encountered problem: " + infoLog);
            }
        }
    }
}
