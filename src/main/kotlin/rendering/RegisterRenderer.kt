package rendering

import common.*
import java.awt.Graphics
import java.awt.Rectangle

class RegisterRenderer {
    var bounds = Rectangle()
    var reg:Register? = null
    fun initRenderer(reg: Register, bounds: Rectangle) {
        this.bounds = bounds
        this.reg = reg
    }

    fun render(g: Graphics) {
        if (reg == null)
            return
        if (reg!!.selected)
            g.color = RegisterBackgroundSel
        else
            g.color = RegisterBackground
        g.fillRect(bounds.x,bounds.y,bounds.width, bounds.height)
        g.color = RegisterBorder
        g.drawRect(bounds.x,bounds.y,bounds.width, bounds.height)
        g.color = RegisterTextNormal
        g.drawString(String.format("%s%x", reg?.name, reg?.reg?.clocked?.toLong()), bounds.x+10, bounds.y + MainFontSize + 2)
    }
}