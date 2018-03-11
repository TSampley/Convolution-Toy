#extension GL_OES_EGL_image_external : require

precision mediump float;

uniform samplerExternalOES u_texture;
uniform vec2 u_radius;

varying vec2 v_texCoord;

void main(void) {
    vec3 orig = texture2D(u_texture, v_texCoord).rgb;
    vec3 sum = -orig;
    int hR = 3;
    int vR = 3;
    for (int i = -hR; i <= hR; i++) {
        float x = v_texCoord.x + float(i)*u_radius.x;
        for (int j = -vR; j <= vR; j++) {
            float y = v_texCoord.y + float(j)*u_radius.y;
            sum = sum + texture2D(u_texture, vec2(x, y)).rgb;
        }
    }
    float divisor = 1.0/float((2*hR+1)*(2*vR+1)-1);

    vec3 avg = divisor*sum;
    vec3 signal = abs(10.0*(avg - orig));
    signal = step(.2, signal)*signal;
    gl_FragColor = vec4(signal, 1);
}