package rendering

import common.*
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

class RomRamRenderer {
    var core: RomRamDecoder? = null
    var bounds = Rectangle()
    var busRenderer = BusRenderer()
    val addrRenderer = RegisterRenderer()
    val bufRenderer = BufferRenderer()
    val valueRenderers = mutableListOf<RegisterRenderer>()
    fun initRenderer(core: RomRamDecoder, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        // Calculate our own width/height
        val addrLeft = bounds.x
        val addrTop = bounds.y
        addrRenderer.initRenderer(core.addrReg, Rectangle(addrLeft, addrTop, 2*RegisterWidth, RegisterHeight))
        for (i in 0 until core.valueRegs.size) {
            valueRenderers.add(RegisterRenderer())
            valueRenderers[i].initRenderer(core.valueRegs[i], Rectangle(addrLeft, addrTop + (i+1)*RegisterHeight,
                2*RegisterWidth, RegisterHeight))
        }
        bounds.height = RegisterHeight * 4
        bufRenderer.initRenderer(core.buffer, Rectangle(addrLeft, addrTop + bounds.height, 2*RegisterWidth, RegisterHeight))
        bounds.height += RegisterHeight
        val busWidth = 20
        val busHeight = 100
        busRenderer.initRenderer(null,
            Point(addrLeft+ RegisterWidth, addrTop + bounds.height),
            Point(addrLeft+ RegisterWidth, addrTop + bounds.height + busHeight), busWidth)
        bounds.height += busHeight
        bounds.width = 2* RegisterWidth
    }

    fun render(g: Graphics) {
        if (core == null) {
            return
        }
        addrRenderer.render(g)
        for (reg in valueRenderers) {
            reg.render(g)
        }
        bufRenderer.render(g)
        busRenderer.render(g, core!!.drivingBus)
        core!!.drivingBus = false
        g.color = TextNormal
        g.drawString(String.format("ROM %d", core!!.getID()), bounds.x, bounds.y + bounds.height - MainFontSize)
    }
}