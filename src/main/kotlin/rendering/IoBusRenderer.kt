package rendering

import common.*
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

class IoBusRenderer {
    var start = Point()
    var end = Point()
    var bounds = Rectangle()
    var bus = Bus()
    var busWidth = 0
    var leds = mutableListOf<IoBitRenderer>()

    fun initRenderer(bus: Bus?, start: Point, end: Point, width: Int, nameBase: String) {
        this.start = start
        this.end = end
        if (bus != null)
            this.bus = bus
        this.busWidth = width
        this.bounds = Rectangle(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.abs(start.x - end.x), Math.abs(start.y - end.y))
        val ledSize = Math.max(Math.abs(end.x-start.x), Math.abs(end.y - start.y))/width
        leds.clear()
        val ledBounds = Rectangle(bounds.x, bounds.y, ledSize, ledSize)
        for (i in 0 until width) {
            val led = IoBitRenderer()
            var name = ""
            if (nameBase.isNotEmpty()) {
                name = String.format("%s%d", nameBase, i)
            }
            led.initRenderer(name, ledBounds)
            leds.add(led)
            ledBounds.x += bounds.width/width
            ledBounds.y += bounds.height/width
        }
    }

    fun render(g: Graphics) {
        for (i in 0 until leds.size) {
            val bit = bus.read().shr(i).and(1U)
            val led = leds[i]
            led.render(g, bit.toByte())
        }
    }

}

class IoBitRenderer {
    var name = ""
    var bounds = Rectangle()

    fun initRenderer(name: String, bounds: Rectangle) {
        this.name = name
        this.bounds = Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
    }

    fun render(g: Graphics, bit: Byte) {
        val centerY = bounds.y + bounds.width / 2
        val radius = bounds.height/2 - 2
        val centerX = bounds.x + radius
        if (bit == 1.toByte()) {
            g.color = LedRedOn
        } else {
            g.color = LedRedOff
        }
        g.fillOval(centerX, centerY, radius*2, radius*2)
        g.color = LedRedBorder
        g.drawOval(centerX, centerY, radius*2+2, radius*2+2)
        g.color = TextNormal
        g.drawString(name, bounds.x + (bounds.width*1.5).toInt(), bounds.y+bounds.height+5)
    }
}