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
import java.awt.Font.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS


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
    var showLa    = false // Show logic analyzer
}

class Visualizer: JFrame() {
    val log = logger()
    var extDataBus = Bus()
    var cpuCore: CpuCore? = null
    var rom0: Rom4001? = null
    var emitter: Emitter<Int>? = null
    var runFlags = RunFlags()
    var laShown = false

    var lastFpsUpdate = 0L
    var fpsCount = 0
    var fps = 0.0

    var renderingBounds = Rectangle()
    var leftRenderingBounds = Rectangle()
    var leftWidth = 1150    // All the CPU stuff

    // Panels
    var cpuPanel = CpuPanel()
    var laPanel = LogicAnalyzerPanel()

    fun run() {
        log.info("Welcome to the 4004 CPU Visualizer")
        prepareGui(runFlags.showLa)
        addKeyListener(MyKeyListener())

        // Create an off-screen buffer to render to
        renderingBounds = Rectangle(0,0, width - insets.left - insets.right, height - insets.top - insets.bottom)
        leftRenderingBounds = Rectangle(renderingBounds)
        leftRenderingBounds.width = leftWidth

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
        cpuPanel.initRenderers()

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
//                Thread.sleep(250)
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
            if (runFlags.showLa != laShown) {
                contentPane.removeAll()
                prepareGui(runFlags.showLa)
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
        System.exit(0)
    }

    fun prepareGui(showLa: Boolean) {

        setLocation(100, 100)
        title = "j4004 CPU Visualizer"

        val mainPanel = JPanel()
        mainPanel.layout = GridBagLayout()
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.BOTH
        c.weighty = 1.0
        c.gridx = 0
        c.gridy = 0
        cpuPanel.preferredSize = Dimension(leftWidth,1000)
        mainPanel.add(cpuPanel, c)

        if (showLa) {
            laPanel.preferredSize = Dimension(400, 800)
            c.gridx = 1
            c.weightx = 1.0
            mainPanel.add(laPanel, c)
            laShown = true
        } else {
            laShown = false
        }
        val mainScroller = JScrollPane(mainPanel)
        add(mainScroller)
        pack()

        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        isVisible = true
        contentPane.revalidate()
        contentPane.repaint()
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
                KeyEvent.VK_L -> {
                    runFlags.showLa = !runFlags.showLa
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

    // Create a panel to hold the CPU/etc
    inner class CpuPanel: JPanel() {
        // Renderables
        var cpuRenderer = CpuCoreRenderer()
        var extBusRenderer = BusRenderer()
        var romRenderer = RomRamRenderer()

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

        override fun paintComponent(g: Graphics?) {
            if (g != null) {
                fpsCount++
                val currTime = System.currentTimeMillis()
                if ((currTime - lastFpsUpdate) > 1000) {
                    fps = 1000.0 * fpsCount.toDouble() / (currTime - lastFpsUpdate).toDouble()
                    lastFpsUpdate = currTime
                    fpsCount = 0
                }
                g.color = Background
                g.fillRect(0,0, bounds.width, bounds.height)
                g.color = TextNormal
                val font = Font(MainFont, BOLD, MainFontSize)
                g.font = font
                romRenderer.render(g)
                extBusRenderer.render(g, false)
                cpuRenderer.render(g)
                g.color = TextNormal
                g.drawString(String.format("FPS=%3.2f", fps), 0, height -insets.top - 24)
            }
        }
    }

    // Create a panel to hold the Logic Analyzer
    inner class LogicAnalyzerPanel: JPanel() {
        init {
            layout = GridBagLayout()
            val c = GridBagConstraints()
            c.fill = GridBagConstraints.BOTH
            c.gridx = 0
            c.gridy = 0
            c.weightx = 1.0
            val titleBox = JLabel("Logic Analyzer")
            titleBox.font = Font(MainFont, BOLD.or(ITALIC), MainFontSize)
            titleBox.background = Background
            add(titleBox, c)
            c.weighty = 1.0
            c.gridy = 1
            val laView = LogicAnalyzerMainView()
            laView.preferredSize = Dimension(1000,1000)
            val scroller = JScrollPane(laView)
            add(scroller, c)
        }
    }

    // Create a panel to hold the Logic Analyzer Scroller
    inner class LogicAnalyzerMainView: JPanel() {
        override fun paintComponent(g: Graphics?) {
            if (g != null) {
                g.color = Color.darkGray
                g.fillRect(0,0,bounds.width, bounds.height)
                g.color = TextNormal
                val font = Font(MainFont, BOLD.or(ITALIC), MainFontSize)
                g.font = font
                g.drawString("Scroll Panel", 100, 30)
                for (i in 0..200) {
                    g.drawString(String.format("%d", i), i * 20, i * 20)
                }
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