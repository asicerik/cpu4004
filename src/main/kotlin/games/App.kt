package games

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWKeyCallbackI
import org.lwjgl.glfw.GLFWWindowSizeCallbackI
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30

abstract class App {
    var width = 1280
    var height = 1280
    var window = 0L
    var lastFpsUpdate = 0L
    var fpsCount = 0
    var fps = 0.0
    var cpuClockRate = 0.0

    fun init() {
        window = opengl.initOpenGl(width, height)

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, KeyCallback())

        // Setup a size change callback
        glfwSetWindowSizeCallback(window, SizeChangeCallback())

        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f)

        createScene()
    }

    fun run() {

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT) // clear the framebuffer

            renderScene()

            glfwSwapBuffers(window)

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents()

            updateFps()
            glfwSetWindowTitle(window, String.format("FPS = %3.2f", fps))
        }
    }

    // createScene is called whenever the scene needs to be recreated
    abstract fun createScene()

    // renderScene is called each frame to draw the components
    abstract fun renderScene()

    // handleKeyEvent is called whenever a key event occurs
    abstract fun handleKeyEvent(window: Long, key: Int, scancode: Int, action: Int, mods: Int)

    // handleScreenSizeChange is called whenever the screen size changes
    abstract fun handleScreenSizeChange(width: Int, height: Int)

    inner class KeyCallback: GLFWKeyCallbackI {
        override fun invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
            handleKeyEvent(window, key, scancode, action, mods)
        }
    }

    inner class SizeChangeCallback: GLFWWindowSizeCallbackI {
        override fun invoke(window: Long, width: Int, height: Int) {
            handleScreenSizeChange(width, height)
        }
    }

    fun updateFps() {
        fpsCount++
        val currTime = System.currentTimeMillis()
        if ((currTime - lastFpsUpdate) > 1000) {
            fps = 1000.0 * fpsCount.toDouble() / (currTime - lastFpsUpdate).toDouble()
            lastFpsUpdate = currTime
            fpsCount = 0
        }
    }
}


