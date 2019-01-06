package rendering

import common.*
import cpucore.CpuCore
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

class CpuCoreRenderer {
    var core: CpuCore? = null
    var bounds = Rectangle()
    val aluRenderer = AluCoreRenderer()
    val instRenderer = InstructionRegRenderer()
    val asRenderer = AddressStackRenderer()
    val indexRenderer = IndexRenderer()
    val bufRenderer = BufferRenderer()

    val intBusRenderer = BusRenderer()
    val ioABusRenderer = BusRenderer()
    val ioBBusRenderer = BusRenderer()

    fun initRenderer(core: CpuCore, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        this.bounds.x += Margin
        val busWidth = 20
        val busHeight = 50
        val intBusWidth = 30
        var left = bounds.x
        var top = bounds.y

        val busX = bounds.width / 2
        ioABusRenderer.initRenderer(null, Point(busX, top), Point(busX, top+busHeight), busWidth)
        top += busHeight
        bufRenderer.initRenderer(core.buffer, Rectangle(busX- RegisterWidth, top, 2* RegisterWidth, RegisterHeight))
        top += RegisterHeight
        ioBBusRenderer.initRenderer(null, Point(busX, top), Point(busX, top+busHeight), busWidth)
        top += busHeight + intBusWidth/2
        intBusRenderer.initRenderer(core.intDataBus, Point(0, top), Point(bounds.width-1, top), intBusWidth)
        top += intBusWidth/2

        // The ALU renderer will compute it's width
        val aluBounds = Rectangle(left, top, 0, 0)
        aluRenderer.initRenderer(core.aluCore, aluBounds)
        left += aluBounds.width

        instRenderer.initRenderer(core.instReg, Rectangle(left, top, RegisterWidth, RegisterHeight))
        left += RegisterWidth + Margin
        asRenderer.initRenderer(core.addrStack, Rectangle(left, top, RegisterWidth, RegisterHeight))
        left += 2 * RegisterWidth + Margin
        indexRenderer.initRenderer(core.indexRegisters, Rectangle(left, top, 0,0))
    }

    fun render(g: Graphics) {
        if (core == null)
            return

        aluRenderer.render(g)
        ioABusRenderer.render(g, false)
        bufRenderer.render(g)
        ioBBusRenderer.render(g, false)
        intBusRenderer.render(g, false)
        instRenderer.render(g)
        indexRenderer.render(g)
        asRenderer.render(g)
        g.color = TextNormal
        g.drawString(String.format("CLK %d", core!!.getClkCount()), instRenderer.bounds.x, instRenderer.bounds.y + instRenderer.bounds.height)
    }
}