package addressstack

import common.Bus
import common.BusWidth
import common.Register
import io.reactivex.Observable
import utils.logger

const val stackDepth = 3

class AddressStack(val dataBus: Bus, clk: Observable<Int>) {
    val log = logger()
    var stackPointer = -1

    val pc = Register(0L, clk)
    val stack = mutableListOf<Register>()

    // Renderer stuff
    var drivingBus = false

    init {
        pc.init(null, 12, "PC  ")
        for (i in 0 until stackDepth) {
            stack.add(Register(0, clk))
            stack[i].init(dataBus, BusWidth, String.format("Level %d ", i))
        }
    }

    fun reset() {
        pc.reset()
        for (i in 0 until stack.size) {
            stack[i].reset()
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
        drivingBus = true
    }

    // Write one nybble of the program counter using the bus as the source
    fun writeProgramCounter(nybble: Int) {
        pc.writeNybbleDirect(nybble, dataBus.read())
    }

    fun stackPush() {
        stackPointer++
        if (stackPointer == stackDepth) {
            log.warn("Stack overflow")
            return
        }
        stack[stackPointer].writeDirect(pc.readDirect())
        log.info(String.format("Stack PUSH: SP=%d (post), PC=%03X", stackPointer, pc.readDirect()))
    }

    fun stackPop() {
        if (stackPointer == -1) {
            log.warn("Stack underflow")
            return
        }
        pc.writeDirect(stack[stackPointer].readDirect())
        log.info(String.format("Stack POP: SP=%d (pre), PC=%03X", stackPointer, pc.readDirect()))
        stack[stackPointer].writeDirect(0)
        stackPointer--
    }

}