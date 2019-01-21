package index

import addressstack.stackDepth
import common.Bus
import common.BusWidth
import common.Register
import io.reactivex.Observable
import utils.logger

const val indexRegisters = 16

class IndexRegisters(val dataBus: Bus, clk: Observable<Int>) {
    val log = logger()
    var index = 0

    val regs = mutableListOf<Register>()

    // Renderer stuff
    var drivingBus = false

    init {
        for (i in 0 until indexRegisters) {
            regs.add(Register(0U, clk))
            regs[i].init(dataBus, BusWidth, "")
        }
    }

    fun reset() {
        for (i in 0 until regs.size) {
            regs[i].reset()
        }
    }

    fun select(index: Int) {
        if (index != this.index && log.isTraceEnabled) {
            log.trace(String.format("Selected register %d", index))
        }
        this.index = index
    }

    fun read() {
        regs[index].read()
        drivingBus = true
    }

    fun readDirect(indexIn: Int): ULong {
        return regs[indexIn].readDirect()
    }

    // This should only be used for testing
    fun writeDirect(indexIn: Int, value: ULong) {
        regs[indexIn].writeDirect(value)
    }

    fun write() {
        regs[index].write()
        if (log.isTraceEnabled) {
            log.trace(String.format("Wrote index register %d with %X", index, dataBus.value))
        }
    }

    fun increment() {
        regs[index].writeDirect((regs[index].readDirect()+1U).and(regs[index].mask))
        if (log.isTraceEnabled) {
            log.trace(String.format("Incremented index register %d. New value is %X", index, regs[index].readDirect()))
        }
    }

    fun isRegisterZero(): Boolean {
        return regs[index].readDirect() == 0UL
    }
}