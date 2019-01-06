package rendering

import addressstack.AddressStack
import addressstack.stackDepth
import common.*
import cpucore.CpuCore
import instruction.InstructionReg
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

class InstructionRegRenderer {
    var core: InstructionReg? = null
    var bounds = Rectangle()
    val instRenderer = RegisterRenderer()
    var busRenderer = BusRenderer()

    fun initRenderer(core: InstructionReg, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        // Calculate our own width/height
        val left = bounds.x
        var top = bounds.y

        val busWidth = 20
        val busHeight = 75
        busRenderer.initRenderer(null,
            Point(left+ RegisterWidth/2, top),
            Point(left+ RegisterWidth/2, top + busHeight), busWidth)
        bounds.height += busHeight
        top += busHeight

        instRenderer.initRenderer(core.inst, Rectangle(left, top, RegisterWidth, RegisterHeight))
        bounds.height += MainFontSize
    }

    fun render(g: Graphics) {
        if (core == null)
            return
        busRenderer.render(g, core!!.drivingBus)
        core!!.drivingBus = false
        instRenderer.render(g)
//        g.color = TextNormal
//        g.drawString("Address Stack", bounds.x+10, bounds.y+bounds.height)
    }

}