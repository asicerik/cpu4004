package instruction

import addressstack.stackDepth
import common.Bus
import common.Register
import io.reactivex.Observable
import utils.logger

class InstructionReg(val dataBus: Bus, clk: Observable<Int>) {
    val log = logger()

    val inst = Register(0L, clk)

    // Renderer stuff
    var drivingBus = false

    init {
        inst.init(null, 8, "INST ")
    }

    fun reset() {
        inst.reset()
    }

    fun getInstructionRegister(): Long {
        return inst.readDirect()
    }

    // Read one nybble of data from the register using the bus as the destination
    fun readInstructionRegister(nybble: Int) {
        val value = inst.readNybbleDirect(nybble)
        dataBus.write(value)
        drivingBus = true
    }

    // Write one nybble of the InstructionRegister using the bus as the source
    fun writeInstructionRegister(nybble: Int) {
        inst.writeNybbleDirect(nybble, dataBus.read())
    }

}