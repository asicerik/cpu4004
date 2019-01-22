package games.game1

import common.Cpu
import games.App
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import opengl.GlObject
import opengl.LedGl
import opengl.compileShader
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30.*
import org.joml.Vector3f
import org.lwjgl.opengl.GL20
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15
import java.io.File
import java.nio.FloatBuffer
import kotlin.system.exitProcess
import kotlin.coroutines.*


fun main(args: Array<String>) {
    val gameApp = Game1App()
    gameApp.initCpu()
    gameApp.runCpu()
    gameApp.init()
    gameApp.run()
}

class Game1App: App() {
    var vao = 0
    val leds = mutableListOf<LedGl>()
    var program = 0
    var activeLed = 0
    var cpu = Cpu()

    init {
        width = 450
        height = 450
    }

    fun initCpu() {
        cpu.init()
        cpu.loadProgram(instruction.genShifter(16))
    }

    fun runCpu() {
        GlobalScope.launch { cpu.run() }
    }

    override fun createScene() {
        vao = glGenVertexArrays()
        glBindVertexArray(vao)
        for (y in 0..7) {
            for (i in 0..7) {
                val led = LedGl()
                val vbos = led.createLedVbo(Vector3f(i * 50f + 50f, y * 50f + 50f, 0f), 15f, 16)
                led.obj.createVbos(vbos.first, vbos.second)
                leds.add(led)
            }
        }
        // Create our program
        program = GL20.glCreateProgram()
        val vs = compileShader("shaders/shader.vert", GL20.GL_VERTEX_SHADER)
        if (vs == 0) {
            exitProcess(-1)
        }
        glAttachShader(program, vs)

        val fs = compileShader("shaders/shader.frag", GL20.GL_FRAGMENT_SHADER)
        if (vs == 0) {
            exitProcess(-1)
        }
        glAttachShader(program, fs)
        glLinkProgram(program)
        glUseProgram(program)

        // Setup our projection
        val fb = BufferUtils.createFloatBuffer(16)
        var m = Matrix4f()
        m = m.ortho2D(0f, width.toFloat(), height.toFloat(), 0f)
        val matLocation = glGetUniformLocation(program, "viewProjMatrix")
        //val colorLocation = glGetUniformLocation(program, "color")
        glUniformMatrix4fv(matLocation, false, m.get(fb))
        //glUniform3f(colorLocation, 0.3f, 0.3f, 0.3f)
    }

    // renderScene is called each frame to draw the components
    override fun renderScene() {
        GL11.glViewport(0,0,width,height)
        glClear(GL11.GL_COLOR_BUFFER_BIT.or(GL11.GL_DEPTH_BUFFER_BIT))
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY)
        for (i in 0 until leds.size) {
            glEnableVertexAttribArray(0)
            glBindBuffer(GL_ARRAY_BUFFER, leds[i].obj.vertexVbo)
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0)

            glEnableVertexAttribArray(1)
            glBindBuffer(GL_ARRAY_BUFFER, leds[i].obj.colorVbo)
            // Copy the updated data
            var on = false
            if (i < 64) {
                on = cpu.ioBus.value.shr(i).and(1U) == 1UL
            } else {
                on = cpu.outputBus.value.shr(i-8).and(1U) == 1UL
            }
            leds[i].set(on)
            if (leds[i].changed)
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, leds[i].obj.colors.toFloatArray(), GL15.GL_STATIC_DRAW)

            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 0, 0)

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 16 * 3)

        }
        glDisableVertexAttribArray(0)
        activeLed++
        if (activeLed == leds.size) {
            activeLed = 0
        }
        cpu.ioBus.write(cpu.ioBus.value.and(0x7fffffffffffffffUL))
    }

    override fun handleKeyEvent(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && action == org.lwjgl.glfw.GLFW.GLFW_RELEASE)
            org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(window, true)
    }

    override fun handleScreenSizeChange(width: Int, height: Int) {
        this.width = width
        this.height = height
        createScene()
    }


}

