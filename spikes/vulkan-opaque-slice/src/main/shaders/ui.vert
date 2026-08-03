#version 450

layout(location = 0) out vec2 textureCoordinate;

void main() {
	vec2 points[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(-1, 1), vec2(-1, 1), vec2(1, -1), vec2(1, 1));
	vec2 coordinates[6] = vec2[](vec2(0, 0), vec2(1, 0), vec2(0, 1), vec2(0, 1), vec2(1, 0), vec2(1, 1));
	gl_Position = vec4(points[gl_VertexIndex], 0, 1);
	textureCoordinate = coordinates[gl_VertexIndex];
}
