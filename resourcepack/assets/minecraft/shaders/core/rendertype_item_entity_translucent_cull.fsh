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

// FFACore core shader: emissive bloom for translucent display entities.
// Translucent block displays (the ability glass) and item displays (the
// crescent projectiles) route through this render type. We keep the vanilla
// tint/light/fog math and gently push the colour toward a soft white-hot
// glow so the glass reads as glowing energy instead of flat tinted glass.
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.02) {
        discard;
    }
    vec3 glow = mix(color.rgb, vec3(1.0), 0.16);
    fragColor = apply_fog(vec4(glow, color.a), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
