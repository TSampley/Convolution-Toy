package com.taushsampley.convolution.renders;

import android.opengl.GLES20;
import android.support.annotation.RawRes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * @author taushsampley
 */

public abstract class Render {

    static final String vertexPosition = "a_position";
    static final String texture = "u_texture";
    static final String textureCoordinates = "a_texCoord";

    static final int TEX_NORMAL = 0;
    static final int TEX_INVERSE = 1;

    private static final int BYTES_PER_FLOAT = 4;

    // "geometry" info
    private static final int vertexCount = 4;
    private static final int vertexOffset = 0;
    private static final int vertexSize = 2;

    private static final int texOffset = vertexSize*vertexCount;
    private static final int texSize = 2;
    private static final int texInverseOffset = texOffset + texSize*vertexCount;

    private static final int stride = 0;
    private static final FloatBuffer rectVertices;

    static {
        float left = -1;
        float right = 1;
        float top = 1;
        float bottom = -1;
        float[] vertices = {
                left, top,              //  (-1, 1)     (1, 1)
                right, top,             //
                left, bottom,           //
                right, bottom           //  (-1, -1)    (1, -1)
        };
        float[] texCoords = {
                // normal
                0, 1,                   //  (0, 1)      (1, 1)
                1, 1,                   //
                0, 0,                   //
                1, 0,                   //  (0, 0)      (1, 0)
                // inverse
                0, 0,                   //  (0, 0)      (1, 0)
                1, 0,                   //
                0, 1,                   //
                1, 1                    //  (0, 1)      (1, 1)
        };
        rectVertices = ByteBuffer.allocateDirect(
                vertexCount*(vertexSize + texSize + texSize)*BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        rectVertices.put(vertices, 0, vertexCount*vertexSize);
        rectVertices.put(texCoords, 0, vertexCount*texSize*2);
    }


    private int width;
    private int height;

    void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public abstract void compileAndLink(CompilationResources resources);
    public abstract void run(RenderResources resources);

    void drawFullscreenQuad(int positionIndex, int textureCoordIndex, int textureCoordinates) {
        // pass vertex position attribute
        rectVertices.position(vertexOffset);
        GLES20.glVertexAttribPointer(positionIndex, vertexSize, GLES20.GL_FLOAT,
                false, stride, rectVertices);
        GLES20.glEnableVertexAttribArray(positionIndex);

        // pass vertex texture attribute
        rectVertices.position(textureCoordinates == TEX_NORMAL ? texOffset : texInverseOffset);
        GLES20.glVertexAttribPointer(textureCoordIndex, texSize, GLES20.GL_FLOAT,
                false, stride, rectVertices);
        GLES20.glEnableVertexAttribArray(textureCoordIndex);

        // draw quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    public static abstract class RenderResources {
        private int displayProgram;
        public RenderResources(int displayProgram) {
            this.displayProgram = displayProgram;
        }
        public abstract int getOutputWidth();
        public abstract int getOutputHeight();
        int getDisplayProgram() {
            return displayProgram;
        }
    }

    public static abstract class CompilationResources extends RenderResources {
        private int texWidth;
        private int texHeight;

        private int vertShader;

        public CompilationResources(int width, int height, int vertShader, int displayProgram) {
            super(displayProgram);
            texWidth = width;
            texHeight = height;
            this.vertShader = vertShader;
        }
        public int getVertexShader() {
            return vertShader;
        }
        public int getTextureWidth() {
            return texWidth;
        }
        public int getTextureHeight() {
            return texHeight;
        }

        public abstract int compileShader(int shaderType, @RawRes int id);
        public abstract int linkProgram(int vertShader, int fragShader);
    }
}
