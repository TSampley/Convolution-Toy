package com.taushsampley.convolution.renders;

import com.tsamp.sproutsocr.R;

/**
 * @author taushsampley
 */

public class LumaRender extends TextureRender {

    public LumaRender(int sourceImageUnit, int targetImageUnit) {
        super(sourceImageUnit, targetImageUnit);
    }

    @Override
    protected int getFragmentSource() {
        return R.raw.luma_frag;
    }

    @Override
    protected void collectIndices() {
        // no indices to collect
    }

    @Override
    protected void prepareRender() {
        // nothing to prepare
    }
}
