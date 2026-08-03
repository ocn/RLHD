#version 450
layout(location = 0) out vec2 texture_coordinate;
void main()
{
	vec2 points[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(-1, 1), vec2(-1, 1), vec2(1, -1), vec2(1, 1));
	vec2 coordinates[6] = vec2[](vec2(0, 0), vec2(1, 0), vec2(0, 1), vec2(0, 1), vec2(1, 0), vec2(1, 1));
	gl_Position = vec4(points[gl_VertexIndex], 0.0, 1.0);
	texture_coordinate = coordinates[gl_VertexIndex];
}
