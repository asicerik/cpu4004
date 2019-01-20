#version 330

// Primary vertex shader
uniform mat4 viewProjMatrix;
in vec3 vert;
in vec3 color;
out vec3 vertColor;

void main(void) {
  gl_Position = viewProjMatrix * vec4(vert,1);
  vertColor = color;
}
