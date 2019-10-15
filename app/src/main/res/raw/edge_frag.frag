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
    if (valRed == 0.0 && valGreen == 0.0) {
        gl_FragColor = vec4(0, 0, 0, 1);
    } else {
        // proceed as normal

        float hue;
        if (valRed == 0.0) {
            if (valGreen > 0.0) {
                hue = 1.5708;
            } else {
                hue = 4.7124;
            }
        } else {
            hue = atan(valGreen, valRed);
            if (hue < 0.0) {
                hue = hue + 6.2832;
            }
        }
        float val = clamp(length(vec2(valRed, valGreen)), 0.0, 1.0);
        float hP = hue / 1.0472;
        float x = val*(1.0 - abs(mod(hP, 2.0) - 1.0));
        vec3 color;
        if (hP <= 1.0) {
            color = vec3(val, x, 0);
        } else if (hP <= 2.0) {
            color = vec3(x, val, 0);
        } else if (hP <= 3.0) {
            color = vec3(0, val, x);
        } else if (hP <= 4.0) {
            color = vec3(0, x, val);
        } else if (hP <= 5.0) {
            color = vec3(x, 0, val);
        } else if (hP <= 6.0) {
            color = vec3(val, 0, x);
        }

        gl_FragColor = vec4(color, 1);
    }
}