package opengl

import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30
import org.slf4j.LoggerFactory
import utils.logger
import java.io.File

fun initOpenGl(width: Int, height: Int): Long {
    // Setup an error callback
    GLFWErrorCallback.createPrint(System.err).set()
    if ( !(GLFW.glfwInit()) ) {
        throw IllegalStateException("Unable to initialize window")
    }
    // setting GLFW window's configuration
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
    GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE) // resizeable window

    val window: Long = GLFW.glfwCreateWindow(width, height, "j4004 Game 1", 0, 0)
    if ( window <= 0 ) {
        println("Failed to create GLFW window")
        GLFW.glfwTerminate()
    }
    // Make the OpenGL context current
    GLFW.glfwMakeContextCurrent(window)

    // Enable v-sync
    GLFW.glfwSwapInterval(1)

    // Make the window visible
    GLFW.glfwShowWindow(window)

    // This line is critical for LWJGL's interoperation with GLFW's
    // OpenGL context, or any context that is managed externally.
    // LWJGL detects the context that is current in the current thread,
    // creates the GLCapabilities instance and makes the OpenGL
    // bindings available for use.
    GL.createCapabilities()

    return window
}

fun compileShader(filename:String, type:Int):Int {
    val log = LoggerFactory.getLogger("GL")
    val source = File(filename).readText()
    //create a shader object
    val shader = GL30.glCreateShader(type)
    //pass the source string
    GL30.glShaderSource(shader, source)
    //compile the source
    GL30.glCompileShader(shader)

    //if info/warnings are found, append it to our shader log
    val infoLog = GL30.glGetShaderInfoLog(shader, GL30.glGetShaderi(shader, GL30.GL_INFO_LOG_LENGTH))
    if (infoLog != null && infoLog!!.trim { it <= ' ' }.length != 0) {
        log.error(infoLog)
        GL30.glDeleteShader(shader)
        return 0
    }
    return shader
}

class GlVertex {
    var v = ArrayList<Float>(3)
    var c = ArrayList<Float>(3)
    fun setLoc(loc: Vector3f) {
        v[0] = loc.x
        v[1] = loc.y
        v[2] = loc.z
    }
    fun setColor(color: Vector3f) {
        c[0] = color.x
        c[1] = color.y
        c[2] = color.z
    }
}

fun setVector(vec: MutableList<Float>, value: Vector3f) {
    vec[0] = value.x
    vec[1] = value.y
    vec[2] = value.z
}

class GlObject {
    var vertexVbo = 0
    var colorVbo  = 0
    var vertices = arrayListOf<Float>()
    var colors   = arrayListOf<Float>()
    fun createVbos(vertices: ArrayList<Float>, colors: ArrayList<Float>) {
        this.vertices = vertices
        this.colors   = colors
        vertexVbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vertexVbo)
        GL30.glEnableVertexAttribArray(0)   // vertices on 0
        glBufferData(GL_ARRAY_BUFFER, vertices.toFloatArray(), GL_STATIC_DRAW)
        glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0)
        colorVbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, colorVbo)
        GL30.glEnableVertexAttribArray(1)   // colors on 1
        glBufferData(GL_ARRAY_BUFFER, colors.toFloatArray(), GL_STATIC_DRAW)
        glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 0, 0)
    }
}
