import common.Bus
import cpucore.CpuCore
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.slf4j.Logger
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
    var extDataBus: Bus? = null
    var cpuCore: CpuCore? = null
    var rom0: Rom4001? = null
    var lastFpsUpdate = 0L
    var fpsCount = 0
    var fps = 0.0
    var drawImage: Image? = null
    var dg: Graphics? = null

    fun run() {
        log.info("Welcome to the 4004 CPU Visualizer")
        prepareGui()

        // Create an off-screen buffer to render to
        drawImage = createImage(width, height)
        dg = drawImage!!.graphics

        var emitter: Emitter<Int>? = null

        var clk: ConnectableObservable<Int> = Observable.create { it: Emitter<Int> ->
            emitter = it
        }.publish()
        clk.connect()
        extDataBus = Bus("Ext Data Bus")
        cpuCore = cpucore.CpuCore(extDataBus!!, clk)
        rom0 = rom4001.Rom4001(extDataBus!!, clk, cpuCore!!.sync, cpuCore!!.cmRom)

        // Create the graphics
        val frame = Visualizer()
        frame.isVisible = true

        val loops = 3200000
        val startTime = System.currentTimeMillis()
        for (i in 0..loops) {
            LogState(cpuCore!!, rom0!!, log)
            extDataBus!!.reset()
            emitter!!.onNext(0)
            //Thread.sleep(100)
            emitter!!.onNext(1)
            //Thread.sleep(100)
//            Thread.sleep(30)
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
                dg!!.color = Color.GRAY
                dg!!.fillRect(0,0, width, height)
                dg!!.color = Color.BLACK
                val font = Font("Serif", PLAIN, 24)
                dg!!.font = font
                dg!!.drawString(String.format("CLK=%d, PC=%d", cpuCore?.getClkCount(), cpuCore?.pc?.clocked), 50, 150)
                dg!!.drawString(String.format("FPS=%3.2f", fps), 20, height - 24)
                g.drawImage(drawImage!!, 0, 0, this)
            }
        }
    }

}

fun LogState(core: cpucore.CpuCore, rom: rom4001.Rom4001, log: Logger) {
    if (log.isInfoEnabled)
        log.info("PC={}, DBUS={}, SYNC={}, CCLK={}, RCLK={}",
            core.pc.clocked,
            Integer.toHexString(core.extDataBus.value),
            core.sync.clocked,
            core.getClkCount(),
            rom.getClkCount())
}