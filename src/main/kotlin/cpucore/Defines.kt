package cpucore

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
const val LD  = 0xA0.toByte() // Load register into accumulator
const val XCH = 0xB0.toByte() // Exchange the accumulator and scratchpad register
const val BBL = 0xC0.toByte() // Branch back (stack pop)
const val LDM = 0xD0.toByte() // Load direct into accumulator
const val WMP = 0xE1.toByte() // RAM Output write
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

// From the instruction decoder
const val AluIntModeNone = 0
const val AluIntModeAdd = 1
const val AluIntModeSub = 2

const val AluAdd = "+"
const val AluSub = "-"
const val AluNone = ""

data class AluFlags(
    var zero: Int,  // The accumulator is zero
    var carry: Int  // The carry bit is set
)

const val FlagPosZero  = 0x2L
const val FlagPosCarry = 0x4L

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
    AccTempSwap,              // Swap the accumulator and temp registers
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


