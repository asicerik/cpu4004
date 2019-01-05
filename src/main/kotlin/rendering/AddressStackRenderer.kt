package rendering

import addressstack.AddressStack
import addressstack.stackDepth
import common.RegisterHeight
import common.RegisterWidth
import common.RomRamDecoder
import cpucore.CpuCore
import java.awt.Graphics
import java.awt.Rectangle

class AddressStackRenderer {
    var core: AddressStack? = null
    var bounds = Rectangle()
    val pcRenderer = RegisterRenderer()
    val stackRenderers = mutableListOf<RegisterRenderer>()

    fun initRenderer(core: AddressStack, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        // Calculate our own width/height
        val pcLeft = bounds.x
        val pcTop = bounds.y
        pcRenderer.initRenderer(core.pc, Rectangle(pcLeft, pcTop, 2*RegisterWidth, RegisterHeight))
        for (i in 0 until stackDepth) {
            stackRenderers.add(RegisterRenderer())
            stackRenderers[i].initRenderer(core.stack[i], Rectangle(pcLeft, pcTop + (i+1)* RegisterHeight,
                2*RegisterWidth, RegisterHeight))
        }
    }

    fun render(g: Graphics) {
        pcRenderer.render(g)
        for (i in 0 until stackDepth) {
            stackRenderers[i].render(g)
        }
    }

}