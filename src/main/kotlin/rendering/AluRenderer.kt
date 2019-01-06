package rendering

import addressstack.AddressStack
import addressstack.stackDepth
import alu.AluCore
import common.*
import cpucore.CpuCore
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

class AluCoreRenderer {
    var core: AluCore? = null
    var bounds = Rectangle()
    var aluBounds = Rectangle()
    val accumRenderer = RegisterRenderer()
    val tempRenderer = RegisterRenderer()
    val flagsRenderer = RegisterRenderer()
    var accumBusRenderer = BusRenderer()
    var tempBusRenderer = BusRenderer()
    val flagsBusRenderer = BusRenderer()
    var accumAluBusRenderer = BusRenderer()
    var tempAluBusRenderer = BusRenderer()
    val aluBusRenderer = BusRenderer()

    fun initRenderer(core: AluCore, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        var left = bounds.x + Margin
        var top = bounds.y

        val busWidth = 20
        val busHeight = 75
        val aluW = 125
        val aluH = 150

        accumBusRenderer.initRenderer(core.accumBus,
            Point(left+ RegisterWidth/2, top),
            Point(left+ RegisterWidth/2, top + busHeight), busWidth)
        accumRenderer.initRenderer(core.accum, Rectangle(left, top+busHeight, RegisterWidth, RegisterHeight))
        val accLeft = left
        left += RegisterWidth + Margin

        tempBusRenderer.initRenderer(core.tempBus,
            Point(left+ RegisterWidth/2, top),
            Point(left+ RegisterWidth/2, top + busHeight), busWidth)
        tempRenderer.initRenderer(core.temp, Rectangle(left, top+busHeight, RegisterWidth, RegisterHeight))
        val tempLeft = left
        left += RegisterWidth + Margin

        flagsBusRenderer.initRenderer(core.flagsBus,
            Point(left+ RegisterWidth/2, top),
            Point(left+ RegisterWidth/2, top + busHeight), busWidth)
        flagsRenderer.initRenderer(core.flags, Rectangle(left, top+busHeight, RegisterWidth, RegisterHeight))
        val flagsLeft = left
        left += RegisterWidth + Margin

        bounds.height = busHeight + RegisterHeight

        val aluX = left
        val aluY = bounds.y + 150
        aluBounds = Rectangle(aluX, aluY, aluW, aluH)

        accumAluBusRenderer.initRenderer(core.accumBus,
            Point(accLeft+ RegisterWidth/2, bounds.y + bounds.height),
            Point(aluX, aluY + (aluH*0.8).toInt()), busWidth)
        accumAluBusRenderer.noStartArrow = true

        tempAluBusRenderer.initRenderer(core.accumBus,
            Point(tempLeft+ RegisterWidth/2, bounds.y + bounds.height),
            Point(aluX, aluY + (aluH*0.2).toInt()), busWidth)
        tempAluBusRenderer.noStartArrow = true

        bounds.height += MainFontSize
        bounds.width = 2 * Margin + 3 * RegisterWidth + aluW

        val aluBusWidth = 50
        aluBusRenderer.initRenderer(core.accumBus,
            Point(bounds.x + bounds.width + Margin, aluY + (aluH*0.5).toInt()),
            Point(bounds.x + bounds.width + Margin + aluBusWidth, top), busWidth)
        aluBusRenderer.noStartArrow = true
        bounds.width += aluBusWidth + busWidth + Margin
    }

    fun render(g: Graphics) {
        if (core == null)
            return
        accumBusRenderer.render(g, core!!.accumDrivingBus)
        accumRenderer.render(g)
        accumAluBusRenderer.render(g, false)
        tempBusRenderer.render(g, core!!.tempDrivingBus)
        tempRenderer.render(g)
        tempAluBusRenderer.render(g, false)
        aluBusRenderer.render(g, core!!.aluDrivingBus)
        renderAlu(g)
        core!!.accumDrivingBus  = false
        core!!.tempDrivingBus   = false
        core!!.flagsDrivingBus  = false
        core!!.aluDrivingBus    = false
    }

    fun renderAlu(g: Graphics) {
        g.color = AluFill
        val left   = aluBounds.x
        val top    = aluBounds.y
        val width  = aluBounds.width
        val height = aluBounds.height
        val xPts = IntArray(7)
        val yPts = IntArray(7)
        xPts[0] = left
        yPts[0] = top
        xPts[1] = left
        yPts[1] = (top+height*0.35).toInt()
        xPts[2] = (left+width*0.4).toInt()
        yPts[2] = (top+height*0.5).toInt()
        xPts[3] = left
        yPts[3] = (top+height*0.65).toInt()
        xPts[4] = left
        yPts[4] = top+height
        xPts[5] = left+width
        yPts[5] = (top+height*0.7).toInt()
        xPts[6] = left+width
        yPts[6] = (top+height*0.3).toInt()
        g.fillPolygon(xPts, yPts, xPts.size)

        if (core != null)
            g.drawString(core!!.mode, (left+width*0.65).toInt(), (top+height*0.5+5).toInt())

    }
}