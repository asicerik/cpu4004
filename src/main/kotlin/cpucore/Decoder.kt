package cpucore

import common.BufDirIn
import common.BufDirNone
import common.BufDirOut
import common.Clocked
import io.reactivex.Observable
import utils.logger
import kotlin.experimental.or

const val NOP = 0x00.toByte() // No Operation
const val JCN = 0x10.toByte() // Jump conditional
const val FIM = 0x20.toByte() // Fetch immediate
const val SRC = 0x21.toByte() // Send address to ROM/RAM
const val FIN = 0x30.toByte() // Fetch indirect from ROM
const val JIN = 0x31.toByte() // Jump indirect from current register pair
const val JUN = 0x40.toByte() // Jump unconditional
const val JMS = 0x50.toByte() // Jump to subroutine
const val INC = 0x60.toByte() // Increment register
const val ISZ = 0x70.toByte() // Increment register and jump if zero
const val ADD = 0x80.toByte() // Add register to accumulator with carry
const val SUB = 0x90.toByte() // Subtract register from accumulator with borrow
const val LDM = 0xD0.toByte() // Load direct into accumulator
const val LD  = 0xA0.toByte() // Load register into accumulator
const val XCH = 0xB0.toByte() // Exchange the accumulator and scratchpad register
const val BBL = 0xC0.toByte() // Branch back (stack pop)
const val WRR = 0xE2.toByte() // ROM I/O write
const val RDR = 0xEA.toByte() // ROM I/O read
const val ACC = 0xF0.toByte() // Alias for all the accumulator instructions
// Accumulator instructions
val CLB = ACC.or(0x0) // Clear accumulator and carry
val CLC = ACC.or(0x1) // Clear carry
val IAC = ACC.or(0x2) // Increment accumulator
val CMC = ACC.or(0x3) // Complement carry
val CMA = ACC.or(0x4) // Complement accumulator
val RAL = ACC.or(0x5) // Rotate left (accumulator and carry)
val RAR = ACC.or(0x6) // Rotate right (accumulator and carry)
val TCC = ACC.or(0x7) // Transmit carry to accumulator and clear carry
val DAC = ACC.or(0x8) // Decrement accumulator
val TCS = ACC.or(0x9) // Transmit carry subtract and clear carry
val STC = ACC.or(0xA) // Set carry
val DAA = ACC.or(0xB) // Decimal adjust
val KBP = ACC.or(0xC) // Keyboard process
val DCL = ACC.or(0xD) // Designate command line

// Some helpers
// You can use any of these three in combination
const val JCN_TEST_SET = 0x11  // Jump if test bit is set
const val JCN_CARRY_SET = 0x12 // Jump if carry bit is set
const val JCN_ZERO_SET = 0x14  // Jump if accumulator is zero
// You can use any of these three in combination
const val JCN_TEST_UNSET = 0x19  // Jump if test bit is NOT set
const val JCN_CARRY_UNSET = 0x1A // Jump if carry bit is NOT set
const val JCN_ZERO_UNSET = 0x1C  // Jump if accumulator is NOT zero

enum class FlagTypes {
    // External I/O Comes first in the list
    Sync,                     // We should output the SYNC signal
    BusDir,                   // External data bus direction
    BusTurnAround,            // If true, swap the bus direction after the first action
    InstRegOut,               // Instruction register should drive the bus (value is the nybble to load)
    InstRegLoad,              // Load the instruction register i/o buffer
    PCOut,                    // Program Counter should drive the bus
    PCLoad,                   // Load the program counter from the internal bus (value is the nybble to load)
    PCInc,                    // Increment the program counter
    AccOut,                   // Accumulator register should drive the bus
    AccLoad,                  // Load the accumulator from the internal bus
    AccInst,                  // Execute an accumulator instruction
    TempLoad,                 // Load the temp register from the internal bus
    TempOut,                  // Temp register should drive the bus
    AluOut,                   // ALU core should drive the bus
    AluEval,                  // ALU should evaluate
    AluMode,                  // The current mode for the ALU
    IndexSelect,              // Which scratchpd (index) register to read/write
    IndexLoad,                // Load 4 bits into the currently selected scratchpad register
    ScratchPadOut,            // Currently selected scratchpad register should drive the bus
    ScratchPadInc,            // Currently selected scratchpad register should be incremented
    StackPush,                // Push the current address onto the stack
    StackPop,                 // Pop the address stack
    DecodeInstruction,        // The instruction register is ready to be decoded
    CmRom,                    // Assert the CMROM signal
    CmRam,                    // Assert one of the CMRAM signals
    EvaluateJCN,              // Evaluate the condition flags for a JCN instruction
    EvaluateISZ,              // Evaluate the scratchpad register for an ISZ instruction
    END                       // Marker for end of list
}

class DecoderFlag(val name:String, var value: Int, val changed: Boolean) {
    val log = logger()

    val resetValue = value
    fun reset() {
        value = resetValue
    }
    fun writeFlag(value: Int) {
        this.value = value
        if (log.isTraceEnabled)
            log.trace(String.format("Wrote Flag: Name=%s, value=%d", name, value))
    }
}


class Decoder(clk: Observable<Int>) {
    val log = logger()

    val clkCount = Clocked(0, clk)
    val flags = mutableMapOf<FlagTypes, DecoderFlag>()
    var syncSent = false

    init {
        createFlags()
    }

    fun createFlags() {
        flags[FlagTypes.Sync]           = DecoderFlag("SYNC", 0, false)
        flags[FlagTypes.BusDir]         = DecoderFlag("BDIR", BufDirNone, false)
        flags[FlagTypes.BusTurnAround]  = DecoderFlag("BTA ", 0, false)
        flags[FlagTypes.InstRegOut]     = DecoderFlag("INSO  ", 0, false)
        flags[FlagTypes.InstRegLoad]    = DecoderFlag("INSL  ", 0, false)
        flags[FlagTypes.PCOut]          = DecoderFlag("PCO ", 0, false)
        flags[FlagTypes.PCLoad]         = DecoderFlag("PCL ", 0, false)
        flags[FlagTypes.PCInc]          = DecoderFlag("PCI ", 0, false)
        flags[FlagTypes.AccOut]         = DecoderFlag("ACCO  ", 0, false)
        flags[FlagTypes.AccLoad]        = DecoderFlag("ACCL  ", 0, false)
        flags[FlagTypes.AccInst]        = DecoderFlag("ACCI  ", -1, false)
        flags[FlagTypes.TempOut]        = DecoderFlag("TMPO  ", 0, false)
        flags[FlagTypes.TempLoad]       = DecoderFlag("TMPL  ", 0, false)
        flags[FlagTypes.AluOut]         = DecoderFlag("ALUO", 0, false)
        flags[FlagTypes.AluEval]        = DecoderFlag("ALUE", 0, false)
        flags[FlagTypes.AluMode]        = DecoderFlag("ALUM", 0, false)
        flags[FlagTypes.IndexSelect]    = DecoderFlag("SPI ", -1, false)
        flags[FlagTypes.IndexLoad]      = DecoderFlag("SPL4", 0, false)
        flags[FlagTypes.ScratchPadOut]  = DecoderFlag("SPO ", 0, false)
        flags[FlagTypes.ScratchPadInc]  = DecoderFlag("SP+ ", 0, false)
        flags[FlagTypes.StackPush]      = DecoderFlag("PUSH", 0, false)
        flags[FlagTypes.StackPop]       = DecoderFlag("POP ", 0, false)
        flags[FlagTypes.DecodeInstruction] = DecoderFlag("DEC ", 0, false)
        flags[FlagTypes.CmRom]          = DecoderFlag("ROM ", 0, false)
        flags[FlagTypes.CmRam]          = DecoderFlag("RAM ", 0, false)
        flags[FlagTypes.EvaluateJCN]    = DecoderFlag("EJCN", 0, false)
        flags[FlagTypes.EvaluateISZ]    = DecoderFlag("EISZ", 0, false)
    }

    fun resetFlags() {
        for (flag in flags) {
            flag.value.reset()
        }
    }

    fun writeFlag(id: FlagTypes, value: Int) {
        val flag = flags[id]
        flag!!.writeFlag(value)
    }

    fun readFlag(id: FlagTypes): Int {
        return flags[id]?.value ?: 0    // 'Elvis' operator. Return 0 if flag is null
    }

    fun clkAndSync() {
        when (clkCount.clocked) {
            6 -> {
                if (syncSent)
                    writeFlag(FlagTypes.PCInc, 1)
                clkCount.raw = clkCount.clocked + 1
            }
            7 -> {
                clkCount.raw = 0
            }
            else -> {
                clkCount.raw = clkCount.clocked + 1
            }
        }
    }

    fun calculateFlags() {
        // NOTE: we are using the raw count here. This allows us to use the next clock cycle count
        // as our index. That way everything is not off by one
        when (clkCount.raw) {
            0 -> {
                writeFlag(FlagTypes.PCOut, 1)   // Output nybble 0
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            1 -> {
                writeFlag(FlagTypes.PCOut, 2)   // Output nybble 1
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            2 -> {
                writeFlag(FlagTypes.PCOut, 3)   // Output nybble 2
                writeFlag(FlagTypes.CmRom, 1)   // Enable the ROM(s)
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            3 -> {
                writeFlag(FlagTypes.BusDir, BufDirIn) // Transfer to the internal bus
            }
            4 -> {
                writeFlag(FlagTypes.InstRegLoad, 2) // load the upper nybble of the inst register
                writeFlag(FlagTypes.BusDir, BufDirIn) // Transfer to the internal bus
            }
            5 -> {
                writeFlag(FlagTypes.InstRegLoad, 1) // load the lower nybble of the inst register
                writeFlag(FlagTypes.DecodeInstruction, 1) // Decode the instruction register
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            6 -> {
                // Could be in or out
                writeFlag(FlagTypes.BusDir, BufDirIn) // Transfer to the internal bus
            }
            7 -> {
                writeFlag(FlagTypes.Sync, 1)    // Generate the sync pulse
                syncSent = true
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
        }
    }


}