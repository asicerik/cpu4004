package rendering

import common.*
import java.awt.Graphics
import java.awt.Rectangle

class BufferRenderer {
    var bounds = Rectangle()
    var buf:Buffer? = null
    fun initRenderer(buf: Buffer, bounds: Rectangle) {
        this.bounds = bounds
        this.buf = buf
    }

    fun render(g: Graphics) {
        if (buf == null)
            return
        g.color = RegisterBackground
        g.fillRect(bounds.x,bounds.y,bounds.width, bounds.height)
        g.color = RegisterBorder
        g.drawRect(bounds.x,bounds.y,bounds.width, bounds.height)
        g.color = RegisterTextNormal
        g.drawString(buf?.name, bounds.x+10, bounds.y + MainFontSize + 2)

        val arrowWidth = 16
        val arrowHeight = 24

        g.color = BufArrowColor
        if (buf!!.dir == BufDirAtoB) {
            renderArrowHead(g, bounds.x+bounds.width-arrowWidth-5, bounds.y+bounds.height-10, arrowHeight, arrowWidth, ArrowDown)
        } else if (buf!!.dir == BufDirBtoA) {
            renderArrowHead(g, bounds.x+bounds.width-arrowWidth-5, bounds.y+bounds.height-arrowHeight-5, arrowHeight, arrowWidth, ArrowUp)
        }

    }
}