#version 450
layout(push_constant) uniform PushConstants { float phase; } push_constants;
layout(location = 0) out vec4 color;
void main()
{
	vec2 points[3] = vec2[](vec2(-0.65, -0.55), vec2(0.0, 0.65), vec2(0.65, -0.55));
	vec2 point = points[gl_VertexIndex];
	float c = cos(push_constants.phase);
	float s = sin(push_constants.phase);
	point = vec2(c * point.x - s * point.y, s * point.x + c * point.y);
	gl_Position = vec4(point, 0.0, 1.0);
	color = vec4(0.15, 0.80, 0.25, 1.0);
}
