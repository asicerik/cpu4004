#version 330

//uniform vec3 color;
in vec3 vertColor;
out vec4 outputColor;

void main(void) {
  outputColor = vec4(vertColor, 1.0);
}