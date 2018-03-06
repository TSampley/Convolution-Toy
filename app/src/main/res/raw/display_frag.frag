#extension GL_OES_EGL_image_external : require

precision mediump float;

uniform samplerExternalOES u_texture;

varying vec2 v_texCoord;

void main(void) {
    gl_FragColor = texture2D(u_texture, v_texCoord);
}