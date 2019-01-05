package rendering

import common.RegisterHeight
import common.RegisterWidth
import common.RomRamDecoder
import java.awt.Graphics
import java.awt.Rectangle

class RomRamRenderer {
    var core: RomRamDecoder? = null
    var bounds = Rectangle()
    val addrRenderer = RegisterRenderer()
    fun initRenderer(core: RomRamDecoder, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        // Calculate our own width/height
        val addrLeft = bounds.x
        val addrTop = bounds.y
        addrRenderer.initRenderer(core.addrReg, Rectangle(addrLeft, addrTop, RegisterWidth, RegisterHeight))
        bounds.height = 100
    }

    fun render(g: Graphics) {
        addrRenderer.render(g)
    }
}