package addressstack

import common.Bus
import common.BusWidth
import common.Register
import io.reactivex.Observable
import utils.logger

const val stackDepth = 3

class AddressStack(val dataBus: Bus, clk: Observable<Int>) {
    val log = logger()
    var stackPointer = 0

    val pc = Register(0L, clk)
    val stack = mutableListOf<Register>()

    init {
        pc.init(null, 12, "PC  ")
        for (i in 0 until stackDepth) {
            stack.add(Register(0, clk))
            stack[i].init(dataBus, BusWidth, String.format("Level %d ", i))
        }
    }

    fun incrementProgramCounter() {
        pc.increment()
    }

    fun getProgramCounter(): Long {
        return pc.readDirect()
    }

    // Read one nybble of data from the register using the bus as the destination
    fun readProgramCounter(nybble: Int) {
        val value = pc.readNybbleDirect(nybble)
        dataBus.write(value)
    }

    // Write one nybble of the program counter using the bus as the source
    fun writeProgramCounter(nybble: Int, value: Long) {
        pc.writeNybbleDirect(nybble, dataBus.read())
    }

    fun stackPush() {
        if (stackPointer == stackDepth) {
            log.warn("Stack overflow")
            return
        }
        log.info(String.format("Stack PUSH: SP=%d (pre), PC=%03X", stackPointer, pc.readDirect()))
        stack[stackPointer].writeDirect(pc.readDirect())
        stackPointer++
    }

    fun stackPop() {
        if (stackPointer == 0) {
            log.warn("Stack underflow")
            return
        }
        pc.writeDirect(stack[stackPointer].readDirect())
        log.info(String.format("Stack POP: SP=%d (pre), PC=%03X", stackPointer, pc.readDirect()))
        stackPointer--
    }

}