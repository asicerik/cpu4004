package rendering

import common.RegisterHeight
import common.RegisterWidth
import common.RomRamDecoder
import cpucore.CpuCore
import java.awt.Graphics
import java.awt.Rectangle

class CpuCoreRenderer {
    var core: CpuCore? = null
    var bounds = Rectangle()
    val asRenderer = AddressStackRenderer()
    fun initRenderer(core: CpuCore, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        // Calculate our own width/height
        val pcLeft = bounds.x
        val pcTop = bounds.y
        asRenderer.initRenderer(core.addrStack, Rectangle(pcLeft, pcTop, RegisterWidth, RegisterHeight))
    }

    fun render(g: Graphics) {
        asRenderer.render(g)
    }

}