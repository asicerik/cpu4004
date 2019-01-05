package rendering

import common.RomRamDecoder
import java.awt.Graphics
import java.awt.Rectangle

class RomRamRenderer(val core: RomRamDecoder) {
    var bounds = Rectangle()
    fun initRenderer(bounds: Rectangle) {
        this.bounds = bounds
    }

    fun render(g: Graphics) {

    }
}