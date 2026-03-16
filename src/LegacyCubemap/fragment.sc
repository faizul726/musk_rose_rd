$input v_fog, relPos, frameTime, fogControl

#include <bgfx_shader.sh>
#include <functions.h>

void main() {
    vec4 albedo = vec4(0.0, 0.0, 0.0, 1.0);

    float time = getTime(v_fog);
    vec3 sunPos = normalize(vec3(cos(time), sin(time), 0.2));
    vec3 moonPos = -sunPos;
    vec3 shadowLightPos = time > 0.0 ? sunPos : moonPos;
    vec2 screenPos = gl_FragCoord.xy;
    float daylight = max(0.0, time);
    float rainLevel = mix(smoothstep(0.5, 0.3, fogControl.x), 0.0, step(fogControl.x, 0.0));

    albedo.rgb = getSky(normalize(relPos), sunPos, moonPos, shadowLightPos, screenPos, daylight, frameTime, rainLevel);

    gl_FragColor = albedo;
}
