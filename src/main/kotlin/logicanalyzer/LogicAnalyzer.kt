package logicanalyzer

import common.MainFontSize
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.util.concurrent.locks.ReentrantLock

data class LogicAnalyzerChannel(
    var name: String,
    var width: Int,
    var value: Long
)

class LogicAnalyzer {
    private var channels = mutableListOf<LogicAnalyzerChannel>()
    private var history = mutableListOf<List<LogicAnalyzerChannel>>()
    private val maxHistory = 16
    private val channelHeight = 50
    private val waveformHeight = 40
    private val cycleWidth = 50
    private var cycle = 0
    private var lock = ReentrantLock()

    fun setChannel(pos: Int, name: String, width: Int, value: Long) {
        if (pos >= channels.size) {
            channels.add(pos, LogicAnalyzerChannel(name, width, value))
        } else {
            channels[pos] = LogicAnalyzerChannel(name, width, value)
        }
        return
    }

    fun runCycle(): Dimension {
        lock.lock()
        // Store the current channels
        val entry = mutableListOf<LogicAnalyzerChannel>()
        entry.addAll(channels)
        history.add(entry)
        if (history.size > maxHistory) {
            history.removeAt(0)
        }
        cycle++
        val res = Dimension((history.size)*cycleWidth, channels.size * channelHeight)
        lock.unlock()
        return res
    }

    fun render(g: Graphics) {
        lock.lock()
        g.color = Color.white
        var left = 40
        var top = 40
        if (history.isNotEmpty()) {
            for (item in history[0]) {
                g.drawString(String.format("%s", item.name), left, top + 25)
                top += channelHeight
            }
        }
        left = 100
        for (i in 0 until history.size) {
            top = 40
            val item = history[i]
            left += cycleWidth
            for (j in 0 until item.size) {
                var changed = false
                if (i > 0) {
                    if (item[j].value != history[i-1][j].value) {
                        changed = true
                    }
                }

                if (item[j].width == 1) {
                    if (item[j].value == 0L) {
                        g.drawLine(left, top + waveformHeight, left + cycleWidth, top + waveformHeight)
                        if (changed)
                            g.drawLine(left, top, left, top + waveformHeight)
                    }
                    else {
                        g.drawLine(left, top, left + cycleWidth, top)
                        if (changed)
                            g.drawLine(left, top + waveformHeight, left, top)
                    }
                } else {
                    // This is a bus
                    val crossWidth = 10
                    if (changed) {
                        g.drawLine(left, top, left + crossWidth, top + waveformHeight)
                        g.drawLine(left + crossWidth, top + waveformHeight, left + cycleWidth, top + waveformHeight)
                        g.drawLine(left, top + waveformHeight, left + crossWidth, top)
                        g.drawLine(left + crossWidth, top, left + cycleWidth, top)
                    } else {
                        g.drawLine(left, top + waveformHeight, left + cycleWidth, top + waveformHeight)
                        g.drawLine(left, top, left + cycleWidth, top)
                    }
                    if (changed || i == 0) {
                        g.drawString(String.format("%X", item[j].value), left + cycleWidth/2, top + MainFontSize+5)
                    }
                }
                top += channelHeight
            }
        }
        lock.unlock()
    }
}