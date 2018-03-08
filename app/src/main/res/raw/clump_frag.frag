#extension GL_OES_EGL_image_external : require

precision mediump float;

uniform samplerExternalOES u_texture;
uniform vec2 u_radius;

varying vec2 v_texCoord;

void main(void) {

    gl_FragColor = vec4(0, 0, 0, 1);

    int hR = 5;
    int vR = 5;
    for (int i = -hR; i <= hR; i++) {
        float x = v_texCoord.x + float(i)*u_radius.x;
        for (int j = -vR; j <= vR; j++) {
            float y = v_texCoord.y + float(j)*u_radius.y;
            gl_FragColor = max(gl_FragColor, texture2D(u_texture, vec2(x, y)));
        }
    }

//    float top = v_texCoord.y + u_radius.y;
//    float bottom = v_texCoord.y - u_radius.y;
//    float left = v_texCoord.x - u_radius.x;
//    float right = v_texCoord.x + u_radius.x;
//
//    // fetch surrounded texels
//    vec4 tl = texture2D(u_texture, vec2(left, top));
//    vec4 tm = texture2D(u_texture, vec2(v_texCoord.x, top));
//    vec4 tr = texture2D(u_texture, vec2(right, top));
//
//    vec4 ml = texture2D(u_texture, vec2(left, v_texCoord.y));
//    vec4 mr = texture2D(u_texture, vec2(right, v_texCoord.y));
//
//    vec4 bl = texture2D(u_texture, vec2(left, bottom));
//    vec4 bm = texture2D(u_texture, vec2(v_texCoord.x, bottom));
//    vec4 br = texture2D(u_texture, vec2(right, bottom));
//
//    gl_FragColor = max(tl, max(tm, max(tr, max(ml, max(mr, max(bl, max(bm, br)))))));
}