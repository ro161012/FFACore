#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;

out vec4 fragColor;

// FFACore core shader: solar-fire grade for translucent display entities.
// Translucent block displays (the ability glass) and item displays (the
// crescent projectiles) route through this render type. We keep the vanilla
// tint/light/fog math, lift the warm red/orange/yellow channels, and blow the
// brightest parts toward white-hot so the ability glass reads as solar fire
// instead of flat tinted glass. Cool colours (Kokushibo purple) stay intact.
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.02) {
        discard;
    }
    float warmth = clamp(color.r - color.b, 0.0, 1.0);
    vec3 graded = color.rgb + vec3(0.55, 0.22, -0.05) * warmth * 0.6;
    float lum = dot(max(graded, vec3(0.0)), vec3(0.2126, 0.7152, 0.0722));
    graded = mix(graded, vec3(1.0), smoothstep(0.45, 0.95, lum) * 0.5);
    fragColor = apply_fog(vec4(graded, color.a), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
