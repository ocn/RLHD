#version 450

layout(location = 0) in vec3 linearColor;
layout(location = 0) out vec4 outputColor;

vec3 linearToSrgb(vec3 linearRgb) {
	return mix(linearRgb * 12.92, 1.055 * pow(linearRgb, vec3(1.0 / 2.4)) - 0.055,
		step(vec3(0.0031308), linearRgb));
}

void main() {
	outputColor = vec4(linearToSrgb(linearColor), 1.0);
}
