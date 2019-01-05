package rendering

import common.*
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

const val ArrowLeft     = 0
const val ArrowRight    = 1
const val ArrowUp       = 2
const val ArrowDown     = 3

class BusRenderer {
    var start = Point()
    var end = Point()
    var bounds = Rectangle()
    var bus = Bus()
    var busWidth = 0
    var arrowWidth = 20         // for l/r arrows
    var arrowHeight = 30        // for u/d we reverse these
    var noStartArrow = false    // Don't show arrow heads
    var noEndArrow = false

    fun initRenderer(bus: Bus, start: Point, end: Point, width: Int, bounds: Rectangle) {
        this.start = start
        this.end = end
        this.bounds = bounds
        this.bus = bus
        this.busWidth = width
        arrowWidth = width
        arrowHeight = (1.5 * width).toInt()
        bounds.x = Math.min(start.x, end.x)
        bounds.y = Math.min(start.y, end.y)
        bounds.width = Math.abs(start.x - end.x)
        bounds.height = Math.abs(start.y - end.y)
    }

    fun render(g: Graphics) {
        g.color = BusBackground
        if (start.y == end.y) {
            if (!noStartArrow)
                renderArrowHead(g, start.x, start.y, arrowWidth, arrowHeight, ArrowLeft)
            else
                g.fillRect(start.x, start.y - busWidth / 2, arrowWidth, busWidth)

            if (!noEndArrow)
                renderArrowHead(g, end.x, start.y, arrowWidth, arrowHeight, ArrowRight)
            else
                g.fillRect(end.x-arrowWidth, start.y - busWidth / 2, arrowWidth, busWidth)

            g.fillRect(start.x + arrowWidth, start.y - busWidth / 2, end.x - start.x - 2 * arrowWidth, busWidth)
            if (bus.name.isNotEmpty()) {
                g.color = RegisterTextNormal
                g.drawString(bus.name, start.x + 20 + arrowWidth, start.y+ MainFontSize/3)
            }
        } else if (start.x == end.x) {
            if (!noStartArrow)
                renderArrowHead(g, start.x, start.y, arrowHeight, arrowWidth, ArrowUp)
            else
                g.fillRect(start.x - busWidth / 2, start.y, busWidth, arrowWidth)

            if (!noEndArrow)
                renderArrowHead(g, end.x, end.y, arrowHeight, arrowWidth, ArrowDown)
            else
                g.fillRect(start.x - busWidth / 2, end.y - arrowWidth, busWidth, arrowWidth)

            g.fillRect(start.x - busWidth / 2, start.y + arrowWidth, busWidth, end.y - start.y - 2 * arrowWidth)
        }
    }

    fun renderArrowHead(g: Graphics, x:Int, y:Int, w:Int, h:Int, dir:Int) {
        val xPts = IntArray(3)
        val yPts = IntArray(3)
        xPts[0] = x
        yPts[0] = y
        when (dir) {
            ArrowLeft -> {
                xPts[1] = x + w
                yPts[1] = y - h/2
                xPts[2] = x + w
                yPts[2] = y + h/2
            }
            ArrowRight -> {
                xPts[1] = x - w
                yPts[1] = y - h/2
                xPts[2] = x - w
                yPts[2] = y + h/2
            }
            ArrowUp -> {
                xPts[1] = x - w/2
                yPts[1] = y + h
                xPts[2] = x + w/2
                yPts[2] = y + h
            }
            ArrowDown -> {
                xPts[1] = x - w/2
                yPts[1] = y - h
                xPts[2] = x + w/2
                yPts[2] = y - h
            }
        }
        g.fillPolygon(xPts, yPts, xPts.size)
    }
}