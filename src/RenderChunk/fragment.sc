$input v_color0, v_fog, v_texcoord0, v_lightmapUV, relPos, fragPos, frameTime, waterFlag, fogControl

#include <bgfx_compute.sh>

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_SeasonsTexture);

#include <functions.h>

void main() {
    vec4 albedo = vec4_splat(0.0);
    vec4 texCol = vec4_splat(0.0);

    #if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY)
        albedo.rgb = vec3_splat(1.0);
    #else
        albedo = texture2D(s_MatTexture, v_texcoord0);
        texCol = albedo;

    #ifdef ALPHA_TEST
        if (albedo.a < 0.5) discard;
    #endif

    #if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
        albedo.rgb *= mix(vec3_splat(1.0), texture2D(s_SeasonsTexture, v_color0.xy).rgb * 2.0, v_color0.b);
        albedo.rgb *= v_color0.aaa;
    #else
        if (abs(v_color0.r - v_color0.g) > 0.001 || abs(v_color0.g - v_color0.b) > 0.001) albedo.rgb *= normalize(v_color0.rgb);
    #endif
    #endif

    #ifndef TRANSPARENT
        albedo.a = 1.0;
    #endif

    /* Hacks */
    bool isUnderwater = fogControl.x == 0.0;
    bool isNether = bool(step(0.1, fogControl.x / fogControl.y) - step(0.12, fogControl.x / fogControl.y));
    bool isTheEnd = (v_fog.r > v_fog.g && v_fog.b > v_fog.g)
        && (greaterThan(v_fog.rgb, vec3(0.03, 0.02, 0.04)) == bvec3(true, true, true))
        && (lessThan(v_fog.rgb, vec3(0.05, 0.04, 0.06)) == bvec3(true, true, true));

    vec3 pos = normalize(relPos);
    vec2 screenPos = gl_FragCoord.xy;
    vec3 viewDir = -pos;

    float time = isTheEnd ? 1.0 : getTime(v_fog);
    vec3 sunPos = normalize(vec3(cos(time), sin(time), 0.2));
    vec3 moonPos = -sunPos;
    vec3 shadowLightPos = time > 0.0 ? sunPos : moonPos;
    float daylight = max(0.0, time);
    float moonHeight = max(0.0, sin(moonPos.y));
    float rainLevel = isTheEnd ? 0.0 : mix(0.0, mix(smoothstep(0.5, 0.3, fogControl.x), 0.0, step(fogControl.x, 0.0)), smoothstep(0.0, 0.94, v_lightmapUV.y));
    float vanillaAO = 0.0;
    
    #ifndef SEASONS
	    vanillaAO = 1.0 - (v_color0.g * 2.0 - (v_color0.r < v_color0.b ? v_color0.r : v_color0.b));
    #endif

    vec3 normal = normalize(cross(dFdx(fragPos), dFdy(fragPos)));
    if (waterFlag > 0.5) {
        albedo.rgb = mix(albedo.rgb, vec3(0.02, 0.1, 0.2), v_lightmapUV.y);
        texCol.rgb = albedo.rgb;
        normal = normalize(mul(getWaterWaveNormal(fragPos.xz, frameTime), getTBNMatrix(normal)));
    } else {
        normal = normalize(mul(getTexNormal(v_texcoord0, 1024.0, 0.0005), getTBNMatrix(normal)));
        #if !defined(ALPHA_TEST) && !defined(TRANSPARENT)
            if ((0.95 < texCol.a && texCol.a < 1.0) && v_color0.r == v_color0.g && v_color0.g == v_color0.b) {
                if (getBlockID(texCol) == 0) { // Iron
                    normal = normalize(mul(getTexNormal(v_texcoord0, 8192.0, 0.0006), getTBNMatrix(normalize(cross(dFdx(fragPos), dFdy(fragPos))))));
                } else if (getBlockID(texCol) == 1) { // Gold
                    normal = normalize(mul(getTexNormal(v_texcoord0, 4096.0, 0.0005), getTBNMatrix(normalize(cross(dFdx(fragPos), dFdy(fragPos))))));
                } else if (getBlockID(texCol) == 2) { // Copper
                    normal = normalize(mul(getTexNormal(v_texcoord0, 2048.0, 0.0005), getTBNMatrix(normalize(cross(dFdx(fragPos), dFdy(fragPos))))));
                } else if (getBlockID(texCol) == 3) { // Others
                    normal = normalize(mul(getTexNormal(v_texcoord0, 4096.0, 0.0005), getTBNMatrix(normalize(cross(dFdx(fragPos), dFdy(fragPos))))));
                }
            }
        #endif
    }
    if (rainLevel > 0.0) {
        normal = normalize(mul(getRainRipplesNormal(normal, fragPos.xz, rainLevel, frameTime), getTBNMatrix(normal)));
    }

    bool isReflective = false;

    float roughness = 0.6;
    float F0 = 0.5;
    #if !defined(ALPHA_TEST) && !defined(TRANSPARENT)
	    if ((0.95 < texCol.a && texCol.a < 1.0) && v_color0.r == v_color0.g && v_color0.g == v_color0.b) {
            if (getBlockID(texCol) == 0) { // Iron
                isReflective = true;
                roughness = 0.09;
                F0 = 0.72;
            } else if (getBlockID(texCol) == 1) { // Gold
                isReflective = true;
                roughness = 0.12;
                F0 = 0.68;
            } else if (getBlockID(texCol) == 2) { // Copper
                isReflective = true;
                roughness = 0.24;
                F0 = 0.52;
            } else if (getBlockID(texCol) == 3) { // Others
                isReflective = true;
                roughness = 0.27;
                F0 = 0.3;
            }
        }
    #endif
    vec3 reflectance = mix(vec3(0.04, 0.04, 0.04), texCol.rgb, F0);
    if (waterFlag > 0.5) {
        roughness = 0.02;
        F0 = 0.6;
        reflectance = vec3(0.6, 0.6, 0.6);
    } else if (rainLevel > 0.0 && v_lightmapUV.y > 0.9375) {
        float rainRipples = mix(0.0, getRainRipples(normalize(cross(dFdx(fragPos), dFdy(fragPos))), fragPos.xz, rainLevel, frameTime), smoothstep(0.90, 0.94, v_lightmapUV.y));
        roughness = mix(roughness, 0.02, rainRipples);
        F0 = mix(F0, 0.9, rainRipples);
    }

    vec4 directionalShadowCol = vec4_splat(0.0);
    float outdoor = isTheEnd ? 1.0 : v_lightmapUV.y;
    float shadow = isTheEnd ? 0.0 : smoothstep(0.94, 0.92, outdoor);
    float diffuse = max(0.0, dot(shadowLightPos, normal));
    directionalShadowCol.a = mix(1.0, shadow, diffuse);

    float pointLightLevel = v_lightmapUV.x * v_lightmapUV.x * v_lightmapUV.x * v_lightmapUV.x * v_lightmapUV.x;

    vec3 directionalLight = vec3_splat(0.0);
    vec3 undirectionalLight = vec3_splat(0.0);

    undirectionalLight += getAmbientLight(pos, sunPos, moonPos, shadowLightPos, daylight, frameTime, rainLevel, moonHeight, outdoor, pointLightLevel) * (1.0 - vanillaAO);
    directionalLight   += getSunlight(daylight, directionalShadowCol, rainLevel);
    directionalLight   += getMoonlight(moonHeight, directionalShadowCol, rainLevel);
    undirectionalLight += isTheEnd ? getSkylight(outdoor, pos, sunPos, moonPos, shadowLightPos, daylight, frameTime, rainLevel) * (1.0 - vanillaAO) : getSkylight(outdoor, pos, sunPos, moonPos, shadowLightPos, daylight, frameTime, rainLevel) * (1.0 - vanillaAO);
    undirectionalLight += getPointLight(pointLightLevel, outdoor, daylight, rainLevel) * (1.0 - vanillaAO);

    vec3 totalLight = undirectionalLight + directionalLight;
    totalLight *= mix(0.65, 1.0, bayerX64(gl_FragCoord.xy) * 0.5 + 0.5);

    albedo.rgb *= totalLight;

    albedo.rgb = uncharted2Tonemap(albedo.rgb, 112.0, 1.25);
    albedo.rgb = hdrExposure(albedo.rgb, 112.0, 1.25);

    vec3 specular = getPBRSpecular(viewDir, shadowLightPos, normal, roughness, reflectance);
    vec3 fresnel = fresnelSchlick(viewDir, normal, reflectance);
    vec3 directionalLightRatio = max(vec3(0.2, 0.2, 0.2) * outdoor, directionalLight / max(vec3(0.001, 0.001, 0.001), totalLight));
    vec3 reflection = mix(albedo.rgb, isTheEnd ? getSkyTheEnd(reflect(pos, normal), sunPos, moonPos, shadowLightPos, screenPos, daylight, frameTime, rainLevel) : getSky(reflect(pos, normal), sunPos, moonPos, shadowLightPos, screenPos, daylight, frameTime, rainLevel), outdoor);
    reflection *= getEnvironmentBRDF(viewDir, normal, roughness, reflectance);

    if (waterFlag > 0.5) {
        albedo.rgb *= 1.0 - reflectance;
        albedo.rgb += (reflection * fresnel) + (10.0 * directionalLightRatio * specular);
        albedo.a = mix(mix(0.1, 1.0, fresnelSchlick(viewDir, normal, F0)), 1.0, 10.0 * directionalLightRatio.r * specular.r);
    } else if (isReflective) {
        albedo.rgb *= 1.0 - reflectance;
        albedo.rgb += (reflection * fresnel) + (mix(3.5, 5.0, rainLevel) * directionalLightRatio * specular);
    } else {
        albedo.rgb += (mix(2.5, 5.0, rainLevel) * directionalLightRatio * specular);
    }

    vec3 fogCol = getSkylightCol(pos, sunPos, moonPos, shadowLightPos, daylight, frameTime, rainLevel);
    fogCol = mix(vec3(getLuma(fogCol), getLuma(fogCol), getLuma(fogCol)), fogCol, outdoor);
    if (isTheEnd) fogCol = getSkylightColTheEnd(pos, sunPos, moonPos, shadowLightPos, daylight, frameTime, rainLevel);
    if (isUnderwater) fogCol = v_fog.rgb; 
    fogCol *= mix(0.65, 1.0, bayerX64(gl_FragCoord.xy) * 0.5 + 0.5);

    albedo.rgb = mix(albedo.rgb, fogCol, v_fog.a);

    gl_FragColor = albedo;
}