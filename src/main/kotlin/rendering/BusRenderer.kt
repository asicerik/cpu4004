package rendering

import common.*
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

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

    fun initRenderer(bus: Bus?, start: Point, end: Point, width: Int) {
        this.start = start
        this.end = end
        if (bus != null)
            this.bus = bus
        this.busWidth = width
        arrowWidth = width
        arrowHeight = (1.5 * width).toInt()
        this.bounds = Rectangle(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.abs(start.x - end.x), Math.abs(start.y - end.y))
    }

    fun render(g: Graphics, drivingBus: Boolean) {
        if (drivingBus)
            g.color = BusBackgroundDriving
        else
            g.color = BusBackground
        if (start.y == end.y) {
            if (!noStartArrow)
                renderArrowHead(g, start.x, start.y, arrowWidth, arrowHeight, ArrowLeft)
            else
                g.fillRect(start.x, start.y - busWidth / 2, arrowWidth, busWidth)

            if (!noEndArrow)
                renderArrowHead(g, end.x, end.y, arrowWidth, arrowHeight, ArrowRight)
            else
                g.fillRect(end.x-arrowWidth, start.y - busWidth / 2, arrowWidth, busWidth)

            g.fillRect(start.x + arrowWidth, start.y - busWidth / 2, end.x - start.x - 2 * arrowWidth, busWidth)
            if (bus.name.isNotEmpty()) {
                g.color = RegisterTextNormal
                g.drawString(String.format("%s %X", bus.name, bus.value.toLong()), start.x + 20 + arrowWidth, start.y+ MainFontSize/3)
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
        } else if (start.x < end.x) {
            if (start.y < end.y) {
                if (!noStartArrow) {
                    renderArrowHead(g, start.x, start.y, arrowHeight, arrowWidth, ArrowUp)
                    g.fillRect(
                        start.x - busWidth / 2, start.y + arrowWidth,
                        busWidth, (end.y - start.y) - arrowWidth)
                } else {
                    g.fillRect(start.x - busWidth / 2, start.y, busWidth, end.y - start.y)
                }
                if (!noEndArrow) {
                    renderArrowHead(g, end.x, end.y, arrowWidth, arrowHeight, ArrowRight)
                    g.fillRect(start.x - busWidth / 2, end.y - busWidth / 2, end.x - start.x, busWidth)
                } else {
                    g.fillRect(start.x - busWidth / 2, end.y - busWidth / 2, end.x - start.x + busWidth / 2, busWidth)
                }
            } else {
                if (!noStartArrow) {
                    renderArrowHead(g, start.x, start.y, arrowWidth, arrowHeight, ArrowLeft)
                    g.fillRect(start.x + arrowWidth, start.y - busWidth / 2,end.x - start.x - arrowWidth, busWidth)
                } else {
                    g.fillRect(start.x, start.y - busWidth / 2,end.x - start.x, busWidth)
                }
                if (!noEndArrow) {
                    renderArrowHead(g, end.x, end.y, arrowHeight, arrowWidth, ArrowUp)
                    g.fillRect(end.x - busWidth / 2, end.y + busWidth / 2, busWidth, Math.abs(end.y - start.y))
                } else {
                    g.fillRect(end.x - busWidth / 2, end.y, busWidth, Math.abs(end.y - start.y - busWidth / 2))
                }
            }
        }
    }

}