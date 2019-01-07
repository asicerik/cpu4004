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

    val decoder = Decoder(clk)
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
            } else {
                clockOut()
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
        if (decoder.readFlag(FlagTypes.DecodeInstruction) != 0) {
            var evalResult = true
//            if c.evaluationFn != nil {
//                evalResult = c.evaluationFn()
//            }
//            c.evaluationFn = nil

            // Write the completed instruction to the decoder
            decoder.setCurrentInstruction(instReg.getInstructionRegister(), evalResult)
        }

        if (decoder.readFlag(FlagTypes.IndexSelect) > 0)
            indexRegisters.select(decoder.readFlag(FlagTypes.IndexSelect))

        if (decoder.readFlag(FlagTypes.PCInc) != 0) {
            addrStack.incrementProgramCounter()
        }
        if (decoder.readFlag(FlagTypes.InstRegLoad) != 0) {
            instReg.writeInstructionRegister(decoder.readFlag(FlagTypes.InstRegLoad)-1)
        }
        if (decoder.readFlag(FlagTypes.AccLoad) != 0) {
            aluCore.writeAccumulator()
        }
        if (decoder.readFlag(FlagTypes.TempLoad) != 0) {
            aluCore.writeTemp()
        }
        if (decoder.readFlag(FlagTypes.AccTempSwap) != 0) {
            aluCore.swap()
        }
        if (decoder.readFlag(FlagTypes.IndexLoad) != 0) {
            indexRegisters.write()
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
        if (decoder.readFlag(FlagTypes.InstRegOut) > 0) {
            instReg.readInstructionRegister(decoder.readFlag(FlagTypes.InstRegOut)-1)
        }
        if (decoder.readFlag(FlagTypes.AccOut) > 0) {
            aluCore.readAccumulator()
        }
        if (decoder.readFlag(FlagTypes.TempOut) > 0) {
            aluCore.readTemp()
        }
        if (decoder.readFlag(FlagTypes.ScratchPadOut) > 0) {
            indexRegisters.read()
        }

    }

    fun clockOut() {
        // Lastly, output to the external bus if needed
        if (decoder.readFlag(FlagTypes.BusDir) == BufDirOut) {
            buffer.bToA()
        } else {
            // Just so the renderer draws the right thing
            buffer.setBusDirectionAToB()
        }
    }
}
