package cpucore

import utils.logger

val NOP = 0x00.toUInt() // No Operation
val JCN = 0x10.toUInt() // Jump conditional
val FIM = 0x20.toUInt() // Fetch immediate
val SRC = 0x21.toUInt() // Send address to ROM/RAM
val FIN = 0x30.toUInt() // Fetch indirect from ROM
val JIN = 0x31.toUInt() // Jump indirect from current register pair
val JUN = 0x40.toUInt() // Jump unconditional
val JMS = 0x50.toUInt() // Jump to subroutine
val INC = 0x60.toUInt() // Increment register
val ISZ = 0x70.toUInt() // Increment register and jump if zero
val ADD = 0x80.toUInt() // Add register to accumulator with carry
val SUB = 0x90.toUInt() // Subtract register from accumulator with borrow
val LD  = 0xA0.toUInt() // Load register into accumulator
val XCH = 0xB0.toUInt() // Exchange the accumulator and scratchpad register
val BBL = 0xC0.toUInt() // Branch back (stack pop)
val LDM = 0xD0.toUInt() // Load direct into accumulator
val IO  = 0xE0.toUInt() // Alias for all the I/O instructions
val WRM = 0xE0.toUInt() // RAM character write
val WMP = 0xE1.toUInt() // RAM Output write
val WRR = 0xE2.toUInt() // ROM I/O write
val WR0 = 0xE4.toUInt() // RAM status 0 write
val WR1 = 0xE5.toUInt() // RAM status 1 write
val WR2 = 0xE6.toUInt() // RAM status 2 write
val WR3 = 0xE7.toUInt() // RAM status 3 write
val SBM = 0xE8.toUInt() // Subtract RAM character from accumulator
val RDM = 0xE9.toUInt() // RAM character read
val RDR = 0xEA.toUInt() // ROM I/O read
val ADM = 0xEB.toUInt() // Add RAM character to accumulator
val RD0 = 0xEC.toUInt() // RAM status 0 read
val RD1 = 0xED.toUInt() // RAM status 1 read
val RD2 = 0xEE.toUInt() // RAM status 2 read
val RD3 = 0xEF.toUInt() // RAM status 3 read
val ACC = 0xF0.toUInt() // Alias for all the accumulator instructions
// Accumulator instructions
val CLB = ACC.or(0x0U) // Clear accumulator and carry
val CLC = ACC.or(0x1U) // Clear carry
val IAC = ACC.or(0x2U) // Increment accumulator
val CMC = ACC.or(0x3U) // Complement carry
val CMA = ACC.or(0x4U) // Complement accumulator
val RAL = ACC.or(0x5U) // Rotate left (accumulator and carry)
val RAR = ACC.or(0x6U) // Rotate right (accumulator and carry)
val TCC = ACC.or(0x7U) // Transmit carry to accumulator and clear carry
val DAC = ACC.or(0x8U) // Decrement accumulator
val TCS = ACC.or(0x9U) // Transmit carry subtract and clear carry
val STC = ACC.or(0xAU) // Set carry
val DAA = ACC.or(0xBU) // Decimal adjust
val KBP = ACC.or(0xCU) // Keyboard process
val DCL = ACC.or(0xDU) // Designate command line

// Some helpers
// You can use any of these three in combination
val JCN_TEST_SET    = 0x11U  // Jump if test bit is set
val JCN_CARRY_SET   = 0x12U  // Jump if carry bit is set
val JCN_ZERO_SET    = 0x14U  // Jump if accumulator is zero
// You can use any of these three in combination
val JCN_TEST_UNSET  = 0x19U  // Jump if test bit is NOT set
val JCN_CARRY_UNSET = 0x1AU  // Jump if carry bit is NOT set
val JCN_ZERO_UNSET  = 0x1CU  // Jump if accumulator is NOT zero

// From the instruction decoder
val AluIntModeNone = 0
val AluIntModeAdd = 1
val AluIntModeSub = 2

val AluAdd = "+"
val AluSub = "-"
val AluNone = ""

data class AluFlags(
    var zero: Int,  // The accumulator is zero
    var carry: Int  // The carry bit is set
)

val FlagPosZero  = 0x2UL
val FlagPosCarry = 0x4UL

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


