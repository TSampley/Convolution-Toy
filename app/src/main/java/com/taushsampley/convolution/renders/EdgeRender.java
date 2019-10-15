package com.taushsampley.convolution.renders;

import android.opengl.GLES20;

import com.tsamp.sproutsocr.R;

/**
 * @author taushsampley
 */

public class EdgeRender extends TextureRender {

    private static final String radiusString = "u_radius";
    private static final String horizontalString = "u_horizontal";
    private static final String verticalString = "u_vertical";

    private int radiusIndex;
    private int horizontalFilterIndex;
    private int verticalFilterIndex;

    private final float[] horizontalFilter;
    private final float[] verticalFilter;

    public EdgeRender(int sourceImageUnit, int targetImageUnit) {
        super(sourceImageUnit, targetImageUnit);
        horizontalFilter = new float[]{
                1, 0, -1,
                2, 0, -2,
                1, 0, -1
        };
        verticalFilter = new float[]{
                1, 2, 1,
                0, 0, 0,
                -1,-2,-1
        };
    }

    @Override
    protected int getFragmentSource() {
        return R.raw.edge_frag;
    }

    @Override
    protected void collectIndices() {
        radiusIndex = GLES20.glGetUniformLocation(getProgram(), radiusString);
        horizontalFilterIndex = GLES20.glGetUniformLocation(getProgram(), horizontalString);
        verticalFilterIndex = GLES20.glGetUniformLocation(getProgram(), verticalString);
    }

    @Override
    protected void prepareRender() {
        float rx = 1.0f / getWidth();
        float ry = 1.0f / getHeight();
        GLES20.glUniform2f(radiusIndex, rx, ry);
        GLES20.glUniformMatrix3fv(horizontalFilterIndex, 1, false,
                horizontalFilter, 0);
        GLES20.glUniformMatrix3fv(verticalFilterIndex, 1, false,
                verticalFilter, 0);
    }
}
