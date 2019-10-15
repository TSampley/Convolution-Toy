package com.taushsampley.convolution.renders;

import android.opengl.GLES20;
import android.util.Log;

import com.tsamp.sproutsocr.R;

/**
 * @author taushsampley
 */

public class ClumpRender extends TextureRender {

    private static final String TAG = "ClumpRender";

    private static final String sampleRadius = "u_radius";

    // edge frag shader
    private int radiusIndex;

    public ClumpRender(int sourceImageUnit, int targetImageUnit) {
        super(sourceImageUnit, targetImageUnit);
    }

    @Override
    protected int getFragmentSource() {
        return R.raw.clump_frag;
    }

    @Override
    protected void collectIndices() {
        int program = getProgram();
        radiusIndex = GLES20.glGetUniformLocation(program, sampleRadius);
    }

    @Override
    protected void prepareRender() {
        float rx = 1.0f / getWidth();
        float ry = 1.0f / getHeight();
        Log.i(TAG, "radius used [" + rx + ", " + ry + "]");
        GLES20.glUniform2f(radiusIndex, rx, ry);
    }
}
