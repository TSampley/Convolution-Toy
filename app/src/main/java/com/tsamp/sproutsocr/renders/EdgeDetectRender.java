package com.tsamp.sproutsocr.renders;

import android.opengl.GLES20;
import android.util.Log;

import com.tsamp.sproutsocr.R;

/**
 * @author taushsampley
 */

public class EdgeDetectRender extends TextureRender {

    private static final String TAG = "EdgeDetectRender";

    private static final String sampleRadius = "u_radius";

    private int radiusIndex;

    public EdgeDetectRender(int sourceImageUnit, int targetImageUnit) {
        super(sourceImageUnit, targetImageUnit);
    }

    @Override
    protected int getFragmentSource() {
        return R.raw.edges_frag;
    }

    @Override
    protected void collectIndices() {
        // no indices to collect
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
