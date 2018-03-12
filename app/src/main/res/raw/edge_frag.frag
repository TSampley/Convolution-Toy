precision mediump float;

uniform sampler2D u_texture;

uniform vec2 u_radius;
uniform mat3 u_horizontal;
uniform mat3 u_vertical;

varying vec2 v_texCoord;

void main(void) {
    float left = v_texCoord.x - u_radius.x;
    float right = v_texCoord.x + u_radius.x;
    float top = v_texCoord.y - u_radius.y;
    float bottom = v_texCoord.y + u_radius.y;

    mat3 vals = mat3(
            texture2D(u_texture, vec2(left, top)).r,
              texture2D(u_texture, vec2(v_texCoord.x, top)).r,
                texture2D(u_texture, vec2(right, top)).r,

            texture2D(u_texture, vec2(left, v_texCoord.y)).r,
              texture2D(u_texture, vec2(v_texCoord.x, v_texCoord.y)).r,
                texture2D(u_texture, vec2(right, v_texCoord.y)).r,

            texture2D(u_texture, vec2(left, bottom)).r,
              texture2D(u_texture, vec2(v_texCoord.x, bottom)).r,
                texture2D(u_texture, vec2(right, bottom)).r);

    float valRed = dot(vals[0], u_horizontal[0])
                    + dot(vals[1], u_horizontal[1])
                    + dot(vals[2], u_horizontal[2]);
    float valGreen = dot(vals[0], u_vertical[0])
                      + dot(vals[1], u_vertical[1])
                      + dot(vals[2], u_vertical[2]);
    gl_FragColor = vec4(abs(valRed), abs(valGreen), 0, 1);
}