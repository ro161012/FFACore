#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec2 texCoord2;

out vec4 fragColor;

// FFACore core shader: applies the entity vertex color (block-display tint
// and glow overrides) to the translucent alpha pass so the Nichirin Blade
// ability effects (Clear Blue Sky fan, Enbu flame ring) render with their
// full colour instead of the unlit default.
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.05) {
        discard;
    }
    fragColor = color;
}
