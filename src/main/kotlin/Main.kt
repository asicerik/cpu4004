import common.*
import cpucore.CpuCore
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
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Font.PLAIN



fun main(args: Array<String>) {
    val visualizer = Visualizer()
    visualizer.run()
}

class Visualizer: Frame() {
    val log = logger()
    var extDataBus = Bus()
    var cpuCore: CpuCore? = null
    var rom0: Rom4001? = null
    var emitter: Emitter<Int>? = null

    var lastFpsUpdate = 0L
    var fpsCount = 0
    var fps = 0.0

    var drawImage: Image? = null
    var dg: Graphics? = null
    var renderingBounds = Rectangle()

    // Renderables
    var cpuRenderer = CpuCoreRenderer()
    var extBusRenderer = BusRenderer()
    var romRenderer = RomRamRenderer()

    fun run() {
        log.info("Welcome to the 4004 CPU Visualizer")
        prepareGui()

        // Create an off-screen buffer to render to
        renderingBounds = Rectangle(0,0, width - insets.left - insets.right, height - insets.top - insets.bottom)
        drawImage = createImage(renderingBounds.width, renderingBounds.height)
        dg = drawImage!!.graphics

        var clk: ConnectableObservable<Int> = Observable.create { it: Emitter<Int> ->
            emitter = it
        }.publish()
        clk.connect()

        extDataBus.init(4, "Ext Data Bus")
        cpuCore = cpucore.CpuCore(extDataBus, clk)
        rom0 = rom4001.Rom4001(extDataBus, clk, cpuCore!!.sync, cpuCore!!.cmRom)

        // Create the graphics
        initRenderers()

        val frame = Visualizer()
        frame.isVisible = true

        val loops = 32
        val startTime = System.currentTimeMillis()
        for (i in 0..loops) {
            LogState(cpuCore!!, rom0!!, log)
            extDataBus.reset()
            emitter!!.onNext(0)
            //Thread.sleep(100)
            emitter!!.onNext(1)
            Thread.sleep(100)
            repaint()
        }
        val endTime = System.currentTimeMillis()
        val interval = (endTime - startTime) / 1000.0

        println(
            String.format(
                "Completed %d cycles in %3.2f seconds. Effective Clk speed %3.2f kHz",
                loops, interval, (loops / interval) / 1000
            )
        )
    }

    fun prepareGui() {
        setSize(1024, 768)
        setLocation(100, 100)
        isVisible = true
        addWindowListener(object: WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                System.exit(0)
            }
        })
    }

    fun initRenderers() {
        val romBounds = Rectangle(0,0, 0, 0)
        romRenderer.initRenderer(rom0!!.decoder, romBounds)
        val extBusBounds = Rectangle(0,romBounds.y + romBounds.height, renderingBounds.width, 0)
        extBusRenderer.initRenderer(extDataBus!!, Point(0, 40), Point(renderingBounds.width, 40), 30, extBusBounds)
        extBusBounds.height = 30
        val cpuBounds = Rectangle(0,extBusBounds.y+extBusBounds.height, 0, 0)
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
                val font = Font(MainFont, PLAIN, MainFontSize)
                dg!!.font = font
                cpuRenderer.render(dg!!)
                romRenderer.render(dg!!)
                extBusRenderer.render(dg!!)
                dg!!.color = TextNormal
                dg!!.drawString(String.format("FPS=%3.2f", fps), 0, height -insets.top - 24)
                g.drawImage(drawImage!!, 0 + insets.left, 0 + insets.top, this)
            }
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