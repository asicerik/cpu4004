package cpucore

import addressstack.AddressStack
import alu.AluCore
import common.*
import index.IndexRegisters
import instruction.InstructionReg
import io.reactivex.Observable
import utils.logger
import java.time.Clock

class CpuCore(val extDataBus: Bus, clk: Observable<Int>) {
    val log = logger()
    // These are public so they can be shared and monitored
    // The contract is that you don't change them :)
    val sync = Clocked(1, clk)      // Sync signal between devices
    val cmRom = Clocked(0, clk)     // ROM select signal from CPU
    val cmRam = Clocked(0, clk)     // RAM select signals (4 bits) from CPU

    private val decoder = Decoder(clk)
    val intDataBus = Bus()
    val buffer = Buffer(extDataBus, intDataBus, "Bus Buffer")
    val aluCore = AluCore(intDataBus, clk)                  // ALU and associate registers
    val instReg = InstructionReg(intDataBus, clk)           // Instruction Register
    val addrStack = AddressStack(intDataBus, clk)           // Address stack for program counter and stack
    val indexRegisters = IndexRegisters(intDataBus, clk)    // Index( scratchpad registers)
    private var syncSent = false

    init {
        intDataBus.init(4, "CPU Internal BUS")
        clk.subscribe {
            // Process on the falling edge of the clock and prepare all data for the rising edge
            if (it==0) {
                process()
            }
        }
    }

    fun getClkCount():Int {
        return decoder.clkCount.clocked
    }

    private fun process() {
        resetFlags()
        decoder.clkAndSync()
        decoder.calculateFlags()
        update()
    }

    private fun resetFlags() {
        intDataBus.reset()
        // Transfer to the internal data bus if needed so the data is available to the decoder
        if (decoder.readFlag(FlagTypes.BusDir) == BufDirIn) {
            buffer.aToB()
        }
        sync.raw = 1
        cmRom.raw = 1
        cmRam.raw = 0xf
        decoder.resetFlags()
    }

    fun update() {
        if (decoder.readFlag(FlagTypes.PCInc) != 0) {
            addrStack.incrementProgramCounter()
        }
        if (decoder.readFlag(FlagTypes.Sync) != 0) {
            sync.raw = 0
        }
        if (decoder.readFlag(FlagTypes.CmRom) != 0) {
            cmRom.raw = 0
        }
        if (decoder.readFlag(FlagTypes.CmRam) > 0) {
            cmRam.raw = decoder.readFlag(FlagTypes.CmRam).inv().and(0xf)
        }

        // Writes to the internal bus
        if (decoder.readFlag(FlagTypes.PCOut) > 0) {
            addrStack.readProgramCounter(decoder.readFlag(FlagTypes.PCOut)-1)
        }

        // Lastly, output to the external bus if needed
        if (decoder.readFlag(FlagTypes.BusDir) == BufDirOut) {
            buffer.bToA()
        } else {
            // Just so the renderer draws the right thing
            buffer.setBusDirectionAToB()
        }
    }
}
