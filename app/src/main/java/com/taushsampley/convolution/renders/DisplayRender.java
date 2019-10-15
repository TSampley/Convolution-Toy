package com.taushsampley.convolution.renders;

import android.opengl.GLES20;

import com.tsamp.sproutsocr.R;

/**
 * @author taushsampley
 */

public class DisplayRender extends Render {

    private int positionIndex;
    private int textureIndex;
    private int textureCoordIndex;

    private int displayExternalProgram;

    @Override
    public void compileAndLink(CompilationResources resources) {
        // retrieve code from files
        int fragShaderHandle = resources.compileShader(GLES20.GL_FRAGMENT_SHADER,
                R.raw.display_frag);
        displayExternalProgram = resources.linkProgram(resources.getVertexShader(), fragShaderHandle);

        positionIndex = GLES20.glGetAttribLocation(displayExternalProgram, vertexPosition);
        textureIndex = GLES20.glGetUniformLocation(displayExternalProgram, texture);
        textureCoordIndex = GLES20.glGetAttribLocation(displayExternalProgram, textureCoordinates);
    }

    @Override
    public void run(RenderResources resources) {
        // display simply draws straight from SurfaceTexture

        // pre-render cleanup
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, resources.getOutputWidth(), resources.getOutputHeight());
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        // select program
        GLES20.glUseProgram(displayExternalProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        // tell sampler identified by `textureIndex` to use texture unit 0
        GLES20.glUniform1i(textureIndex, 0);

        drawFullscreenQuad(positionIndex, textureCoordIndex, TEX_INVERSE);
    }
}
