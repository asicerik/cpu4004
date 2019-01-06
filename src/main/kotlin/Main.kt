import common.*
import cpucore.CpuCore
import instruction.genLEDCount
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.slf4j.Logger
import rendering.BusRenderer
import rendering.CpuCoreRenderer
import rendering.RomRamRenderer
import rom4001.Rom4001
import utils.logger
import java.awt.*
import java.awt.Font.BOLD
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Font.PLAIN
import java.awt.event.KeyEvent
import java.awt.event.KeyListener


fun main(args: Array<String>) {
    val visualizer = Visualizer()
    visualizer.run()
}

class RunFlags {
    var stepClock = false // Step one clock
    var stepCycle = false // Step 8 clocks
    var freeRun   = false // Let 'er rip!
    var halt      = false // Stop the processor
    var quit      = false // Quit the program
}


class Visualizer: Frame() {
    val log = logger()
    var extDataBus = Bus()
    var cpuCore: CpuCore? = null
    var rom0: Rom4001? = null
    var emitter: Emitter<Int>? = null
    var runFlags = RunFlags()

    var lastFpsUpdate = 0L
    var fpsCount = 0
    var fps = 0.0

    var drawImage: Image? = null
    var dg: Graphics? = null
    var renderingBounds = Rectangle()
    var leftRenderingBounds = Rectangle()
    var leftWidth = 1150    // All the CPU stuff

    // Renderables
    var cpuRenderer = CpuCoreRenderer()
    var extBusRenderer = BusRenderer()
    var romRenderer = RomRamRenderer()

    fun run() {
        log.info("Welcome to the 4004 CPU Visualizer")
        prepareGui()

        // Create an off-screen buffer to render to
        renderingBounds = Rectangle(0,0, width - insets.left - insets.right, height - insets.top - insets.bottom)
        leftRenderingBounds = Rectangle(renderingBounds)
        leftRenderingBounds.width = leftWidth
        drawImage = createImage(renderingBounds.width, renderingBounds.height)
        dg = drawImage!!.graphics

        var clk: ConnectableObservable<Int> = Observable.create { it: Emitter<Int> ->
            emitter = it
        }.publish()
        clk.connect()

        extDataBus.init(4, "Ext Data Bus")
        cpuCore = cpucore.CpuCore(extDataBus, clk)
        rom0 = rom4001.Rom4001(extDataBus, clk, cpuCore!!.sync, cpuCore!!.cmRom)

        // Load the ROMs
        rom0!!.loadProgram(genLEDCount())

        // Create the graphics
        initRenderers()

        val frame = Visualizer()
        frame.isVisible = true

        val startTime = System.currentTimeMillis()
        var cycleCount = 0
        repaint()
        while (!runFlags.quit) {
            if (runFlags.stepClock || runFlags.stepCycle || runFlags.freeRun) {
                LogState(cpuCore!!, rom0!!, log)
                extDataBus.reset()
                emitter!!.onNext(0)
                //Thread.sleep(100)
                emitter!!.onNext(1)
                Thread.sleep(250)
                repaint()
                runFlags.stepClock = false
                cycleCount++
                if (cycleCount == 8) {
                    cycleCount = 0
                    runFlags.stepCycle = false
                }
            } else {
                Thread.sleep(25)
            }
        }
        val endTime = System.currentTimeMillis()
        val interval = (endTime - startTime) / 1000.0

//        println(
//            String.format(
//                "Completed %d cycles in %3.2f seconds. Effective Clk speed %3.2f kHz",
//                loops, interval, (loops / interval) / 1000
//            )
//        )
    }

    fun prepareGui() {
        setSize(1920, 1024)
        setLocation(100, 100)
        isVisible = true
        addWindowListener(object: WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                System.exit(0)
            }
        })
        addKeyListener(MyKeyListener())
    }

    fun initRenderers() {
        val romBounds = Rectangle(Margin,Margin, 0, 0)
        romRenderer.initRenderer(rom0!!.decoder, romBounds)
        val extBusWidth = 30
        val extBusBounds = Rectangle(0,romBounds.y + romBounds.height + extBusWidth/2, leftRenderingBounds.width, 0)
        extBusRenderer.initRenderer(extDataBus!!, Point(extBusBounds.x, extBusBounds.y), Point(leftRenderingBounds.width, extBusBounds.y), 30)
        extBusBounds.height = 10
        val cpuBounds = Rectangle(0,extBusBounds.y+extBusBounds.height, leftRenderingBounds.width, 0)
        cpuRenderer.initRenderer(cpuCore!!, cpuBounds)
    }

    override fun update(g: Graphics?) {
        if (g != null) {
            fpsCount++
            val currTime = System.currentTimeMillis()
            if ((currTime - lastFpsUpdate) > 1000) {
                fps = 1000.0 * fpsCount.toDouble() / (currTime - lastFpsUpdate).toDouble()
                lastFpsUpdate = currTime
                fpsCount = 0
            }
            if (dg != null) {
                dg!!.color = Background
                dg!!.fillRect(0,0, width, height)
                dg!!.color = TextNormal
                val font = Font(MainFont, BOLD, MainFontSize)
                dg!!.font = font
                romRenderer.render(dg!!)
                extBusRenderer.render(dg!!, false)
                cpuRenderer.render(dg!!)
                dg!!.color = TextNormal
                dg!!.drawString(String.format("FPS=%3.2f", fps), 0, height -insets.top - 24)
                g.drawImage(drawImage!!, 0 + insets.left, 0 + insets.top, this)
            }
        }
    }

    inner class MyKeyListener: KeyListener {
        override fun keyTyped(e: KeyEvent?) {
        }

        override fun keyPressed(e: KeyEvent?) {
            val keyCode = e?.keyCode
            when (keyCode) {
                KeyEvent.VK_S -> {
                    runFlags.stepCycle = true
                }
                KeyEvent.VK_C -> {
                    runFlags.stepClock = true
                }
                KeyEvent.VK_R -> {
                    runFlags.freeRun = true
                }
                KeyEvent.VK_Q -> {
                    runFlags.quit = true
                }
                KeyEvent.VK_ESCAPE -> {
                    runFlags.quit = true
                }
            }
        }

        override fun keyReleased(e: KeyEvent?) {
        }
    }

}

fun LogState(core: cpucore.CpuCore, rom: rom4001.Rom4001, log: Logger) {
    if (log.isInfoEnabled)
        log.info("PC={}, DBUS={}, SYNC={}, CCLK={}, RCLK={}",
            core.addrStack.getProgramCounter(),
            Integer.toHexString(core.extDataBus.value.toInt()),
            core.sync.clocked,
            core.getClkCount(),
            rom.getClkCount())
}