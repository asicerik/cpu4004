package rendering

import java.awt.Graphics

const val ArrowLeft     = 0
const val ArrowRight    = 1
const val ArrowUp       = 2
const val ArrowDown     = 3

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
