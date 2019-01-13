package common

import io.reactivex.Observable
import utils.logger

class Register(initialVal: Long, clk: Observable<Int>): Maskable() {
    val log = logger()

    var dataBus:Bus? = null
    var reg = Clocked(initialVal, clk)

    // Renderer bits
    var selected = false
    val changed  = false

    fun init(dataBus: Bus?, width: Int, name: String) {
        this.dataBus = dataBus
        super.baseInit(width, name)
    }

    fun reset() {
        reg.reset()
    }

    // Read one nybble of data from the register using the bus as the destination
    fun readNybble(nybble: Int) {
        if (dataBus != null) {
            val value = readNybbleDirect(nybble)
            dataBus?.write(value)
        }
    }

    // Read data from the register using the bus as the destination
    fun read() {
        if (dataBus != null) {
            val value = reg.clocked.and(dataBus!!.mask)
            dataBus?.write(value)
        }
    }

    // Write one nybble data into the register using the bus as the source
    fun writeNybble(nybble: Int) {
        if (dataBus != null)
            writeNybbleDirect(nybble, dataBus!!.read())
    }

    // Write data into the register using the bus as the source
    fun write() {
        if (dataBus != null)
            reg.raw = dataBus!!.read().and(mask)
    }

    // ReadDirect directly reads the register instead of using the bus
    fun readDirect(): Long {
        return reg.clocked.and(mask)
    }

    // ReadDirectRaw directly reads the registers input
    // This should be used very carefull
    fun readDirectRaw(): Long {
        return reg.raw.and(mask)
    }

    // ReadDirect directly reads one nybble of the register instead of using the bus
    fun readNybbleDirect(nybble: Int): Long {
        return reg.clocked.shr(nybble * 4).and(0xf)
    }

    // WriteDirect directly writes the register instead of using the bus
    fun writeDirect(value: Long) {
        reg.raw = value.and(mask)
    }

    // writeNybbleDirect directly writes one nybble of the register instead of using the bus
    fun writeNybbleDirect(nybble: Int, value: Long) {
        var curr = reg.raw
        val wrMask = 0xf.toLong().shl(nybble*4)
        val rdMask = wrMask.inv().and(mask)
        reg.raw = (value.shl(nybble*4).and(wrMask).or(curr.and(rdMask)))
    }

    // Increment directly increments the value in a register
    // Not sure all hardware supports this
    fun increment() {
        reg.raw = (reg.clocked + 1).and(mask)
    }
}
