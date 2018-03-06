#extension GL_OES_EGL_image_external : require

precision mediump float;

uniform samplerExternalOES u_texture;
uniform vec2 u_radius;

varying vec2 v_texCoord;

void main(void) {
    float top = v_texCoord.y + u_radius.y;
    float bottom = v_texCoord.y - u_radius.y;
    float left = v_texCoord.x - u_radius.x;
    float right = v_texCoord.x + u_radius.x;

    // fetch surrounded texels
    vec4 tl = texture2D(u_texture, vec2(left, top));
    vec4 tm = texture2D(u_texture, vec2(v_texCoord.x, top));
    vec4 tr = texture2D(u_texture, vec2(right, top));

    vec4 ml = texture2D(u_texture, vec2(left, v_texCoord.y));
    vec4 mr = texture2D(u_texture, vec2(right, v_texCoord.y));

    vec4 bl = texture2D(u_texture, vec2(left, bottom));
    vec4 bm = texture2D(u_texture, vec2(v_texCoord.x, bottom));
    vec4 br = texture2D(u_texture, vec2(right, bottom));

    vec3 orig = texture2D(u_texture, v_texCoord).rgb;
    vec3 avg = (tl+tm+tr+ml+mr+bl+bm+br).rgb/vec3(8, 8, 8);
    vec3 signal = abs(10.0*(avg - orig));
    signal = step(.2, signal)*signal;
    gl_FragColor = vec4(signal, 1);
}