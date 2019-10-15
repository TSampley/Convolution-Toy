package com.taushsampley.convolution.renders;

import android.opengl.GLES20;
import android.util.Log;

import com.taushsampley.convolution.R;


/**
 * @author taushsampley
 */

public class AverageDifferenceRender extends TextureRender {

    private static final String TAG = "AverageDifferenceRender";

    private static final String sampleRadius = "u_radius";

    private int radiusIndex;

    public AverageDifferenceRender(int sourceImageUnit, int targetImageUnit) {
        super(sourceImageUnit, targetImageUnit);
    }

    @Override
    protected int getFragmentSource() {
        return R.raw.avg_diff;
    }

    @Override
    protected void collectIndices() {
        radiusIndex = GLES20.glGetUniformLocation(getProgram(), sampleRadius);
    }

    @Override
    protected void prepareRender() {
        float rx = 1.0f / getWidth();
        float ry = 1.0f / getHeight();
        Log.i(TAG, "radius used [" + rx + ", " + ry + "]");
        GLES20.glUniform2f(radiusIndex, rx, ry);
    }
}
