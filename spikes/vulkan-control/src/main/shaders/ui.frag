#version 450
layout(set = 0, binding = 0) uniform sampler2D ui_texture;
layout(location = 0) in vec2 texture_coordinate;
layout(location = 0) out vec4 output_color;
void main()
{
	output_color = texture(ui_texture, texture_coordinate);
}
