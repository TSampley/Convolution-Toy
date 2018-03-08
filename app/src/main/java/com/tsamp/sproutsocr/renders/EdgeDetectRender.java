package com.tsamp.sproutsocr.renders;

import android.opengl.GLES20;
import android.util.Log;

import com.tsamp.sproutsocr.R;

/**
 * @author taushsampley
 */

public class EdgeDetectRender extends Render {

    private static final String TAG = "EdgeDetectRender";

    private static final String sampleRadius = "u_radius";

    // vertex shader
    private int positionIndex;
    private int textureCoordIndex;

    // edge frag shader
    private int externalTextureIndex;
    private int radiusIndex;

    // display frag shader
    private int texture2DIndex;

//    private int edgeFramebufferHandle;
//    private int edgeTextureHandle;

    private int edgeProgram;
    private int displayProgram;

    private final int imageUnit;

    public EdgeDetectRender(int imageUnit) {
        this.imageUnit = imageUnit;
    }

    @Override
    public void compileAndLink(CompilationResources resources) {
        int width = resources.getTextureWidth();
        int height = resources.getTextureHeight();
        setSize(width, height);

        // edge detection fragment shader
        int edgesFragHandle = resources.compileShader(GLES20.GL_FRAGMENT_SHADER,
                R.raw.edges_frag);
        // display fragment shader
        int display2DFragHandle = resources.compileShader(GLES20.GL_FRAGMENT_SHADER,
                R.raw.display_2d_frag);

        edgeProgram = resources.linkProgram(resources.getVertexShader(), edgesFragHandle);
        displayProgram = resources.linkProgram(resources.getVertexShader(), display2DFragHandle);

        // vertex shader
        positionIndex = GLES20.glGetAttribLocation(edgeProgram, vertexPosition);
        textureCoordIndex = GLES20.glGetAttribLocation(edgeProgram, textureCoordinates);
        // edge frag shader
        externalTextureIndex = GLES20.glGetUniformLocation(edgeProgram, texture);
        radiusIndex = GLES20.glGetUniformLocation(edgeProgram, sampleRadius);
        // display frag shader
        texture2DIndex = GLES20.glGetUniformLocation(displayProgram, texture);

        // ============================= Framebuffer creation
        // create Framebuffer
        int[] handles = new int[1];
        GLES20.glGenFramebuffers(1, handles, 0);
        int edgeFramebufferHandle = handles[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, edgeFramebufferHandle);

        // create Texture to render into
        GLES20.glGenTextures(1, handles, 0);
        int edgeTextureHandle = handles[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + imageUnit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, edgeTextureHandle);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height,
                0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
//
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, edgeTextureHandle, 0);
    }

    @Override
    public void run(RenderResources resources) {
        // needs to first draw to the external texture from SurfaceTexture
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 1);
        GLES20.glViewport(0, 0, getWidth(), getHeight());
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(edgeProgram);

        float rx = 1.0f / getWidth();
        float ry = 1.0f / getHeight();
        Log.i(TAG, "radius used [" + rx + ", " + ry + "]");
        GLES20.glUniform2f(radiusIndex, rx, ry);
        // tell samppler identified by `externalTextureIndex` to use texture unit 0
        GLES20.glUniform1i(externalTextureIndex, 0);

        drawFullscreenQuad(positionIndex, textureCoordIndex, TEX_INVERSE);


        // now draw to actual GLSurfaceView
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, resources.getOutputWidth(), resources.getOutputHeight());
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // select program
        GLES20.glUseProgram(displayProgram);

        // tell sampler identified by `texture2DIndex` to use texture unit 1
        GLES20.glUniform1i(texture2DIndex, 1);

        drawFullscreenQuad(positionIndex, textureCoordIndex, TEX_NORMAL);
    }
}
