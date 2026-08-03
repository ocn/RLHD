#version 450

layout(location = 0) in ivec4 packedPosition;
layout(location = 1) in vec4 packedUvw;
layout(location = 2) in ivec4 packedNormal;
layout(location = 3) in int faceRef;

layout(set = 0, binding = 0, std430) readonly buffer FaceMetadata {
	int words[];
};

layout(push_constant) uniform PushConstants {
	mat4 clipFromWorld;
	ivec2 sceneBase;
} pushConstants;

layout(location = 0) out vec3 linearColor;

vec3 hslToSrgb(vec3 hsl) {
	float chroma = (1.0 - abs(2.0 * hsl.z - 1.0)) * hsl.y;
	float hue = fract(hsl.x) * 6.0;
	float offset = hsl.z - chroma * 0.5;
	vec3 rgb = vec3(
		clamp(abs(hue - 3.0) - 1.0, 0.0, 1.0),
		clamp(2.0 - abs(hue - 2.0), 0.0, 1.0),
		clamp(2.0 - abs(hue - 4.0), 0.0, 1.0));
	return rgb * chroma + offset;
}

vec3 srgbToLinear(vec3 srgb) {
	return mix(srgb / 12.92, pow((srgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), srgb));
}

vec3 packedHslToLinear(int packedHsl) {
	vec3 raw = vec3(float((packedHsl >> 10) & 63), float((packedHsl >> 7) & 7), float(packedHsl & 127));
	vec3 hsl = vec3(raw.x / 64.0 + 0.0078125, raw.y / 8.0 + 0.0625, raw.z / 128.0);
	return srgbToLinear(hslToSrgb(hsl));
}

void main() {
	int corner = gl_VertexIndex % 3;
	int ref = faceRef & 0x7fffffff;
	if (faceRef < 0) corner = 2 - corner;
	int baseInt = ref * 3;
	int alphaBiasHsl = words[baseInt + corner];
	int material = words[baseInt + 3 + corner];
	int terrain = words[baseInt + 6 + corner];

	vec3 worldPosition = vec3(packedPosition.xyz) + vec3(pushConstants.sceneBase.x, 0, pushConstants.sceneBase.y);
	worldPosition += packedUvw.xyz * 0.0 + vec3(packedNormal.xyz) * 0.0;
	linearColor = packedHslToLinear(alphaBiasHsl & 0xffff);
	linearColor += vec3(float((material & 0) + (terrain & 0)));

	vec4 clip = pushConstants.clipFromWorld * vec4(worldPosition, 1.0);
	int depthBias = (alphaBiasHsl >> 16) & 0xff;
	if (pushConstants.clipFromWorld[2][3] != 0.0) clip.z += float(depthBias) / 128.0;
	clip.z = 0.5 * (clip.z + clip.w);
	gl_Position = clip;
}
