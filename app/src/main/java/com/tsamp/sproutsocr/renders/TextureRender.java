package com.tsamp.sproutsocr.renders;

import android.opengl.GLES20;
import android.support.annotation.CallSuper;
import android.support.annotation.RawRes;

/**
 * @author taushsampley
 */

public abstract class TextureRender extends Render {

    // given
    private int sourceImageUnit;
    private int targetImageUnit;

    // will be generated
    private int program;
    private int positionIndex;
    private int textureCoordIndex;
    private int sourceTextureIndex;
    private int outputTextureIndex;
    private int targetFramebuffer;

    TextureRender(int sourceImageUnit, int targetImageUnit) {
        this.sourceImageUnit = sourceImageUnit;
        this.targetImageUnit = targetImageUnit;
    }

    protected abstract @RawRes int getFragmentSource();
    protected abstract void collectIndices();
    protected abstract void prepareRender();

    int getProgram() {
        return program;
    }

    @CallSuper
    @Override
    public void compileAndLink(CompilationResources resources) {
        // get size and store it to use for setting viewport
        int width = resources.getTextureWidth();
        int height = resources.getTextureHeight();
        setSize(width, height);

        // compile custom fragment shader
        int fragHandle = resources.compileShader(GLES20.GL_FRAGMENT_SHADER,
                getFragmentSource());
        program = resources.linkProgram(resources.getVertexShader(), fragHandle);

        // vertex shader
        positionIndex = GLES20.glGetAttribLocation(program, vertexPosition);
        textureCoordIndex = GLES20.glGetAttribLocation(program, textureCoordinates);
        // edge frag shader
        sourceTextureIndex = GLES20.glGetUniformLocation(program, texture);
        // display frag shader
        outputTextureIndex = GLES20.glGetUniformLocation(resources.getDisplayProgram(), texture);

        // allow inheriting classes to collect their custom program indices
        collectIndices();

        if (positionIndex == -1 || textureCoordIndex == -1 ||
                sourceTextureIndex == -1 || outputTextureIndex == -1) {
            throw new IllegalStateException(
                    "positionIndex:"+positionIndex+
                    " textureCoordIndex:"+textureCoordIndex+
                    " sourceTextureIndex:"+sourceTextureIndex+
                    " outputTextureIndex:"+outputTextureIndex);
        }

        // ============================= Framebuffer creation
        // create Framebuffer
        int[] handles = new int[1];
        GLES20.glGenFramebuffers(1, handles, 0);
        targetFramebuffer = handles[0];
        // set current Framebuffer to the newly created one
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer);

        // create Texture to render into
        GLES20.glGenTextures(1, handles, 0);
        int targetTextureIndex = handles[0];
        // set active image unit to the one passed in
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + targetImageUnit);
        // bind the the newly generated texture object to the 2D target
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTextureIndex);
        // fill with initial texture data
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height,
                0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null);
        // set sampling parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);

        // connect the texture to the framebuffer
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, targetTextureIndex, 0);
    }

    @CallSuper
    @Override
    public void run(RenderResources resources) {
        // needs to first draw to the target texture from source texture
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer);
        GLES20.glViewport(0, 0, getWidth(), getHeight());
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);

        // tell sampler identified by `sourceTextureIndex` to use image unit `sourceImageUnit`
        GLES20.glUniform1i(sourceTextureIndex, sourceImageUnit);

        // let descendants prepare their own arguments before drawing
        prepareRender();

        drawFullscreenQuad(positionIndex, textureCoordIndex, TEX_NORMAL);

        // now draw to actual GLSurfaceView
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, resources.getOutputWidth(), resources.getOutputHeight());
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // select program
        GLES20.glUseProgram(resources.getDisplayProgram());

        // tell sampler identified by `texture2DIndex` to use texture unit 1
        GLES20.glUniform1i(outputTextureIndex, targetImageUnit);

        drawFullscreenQuad(positionIndex, textureCoordIndex, TEX_INVERSE);
    }
}
