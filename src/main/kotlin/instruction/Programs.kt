package instruction

import cpucore.*
import kotlin.experimental.or

fun genLEDCount(): List<Byte> {
    val data = mutableListOf<Byte>()
    for (i in 0 until 16) {
        addInstruction(data, LDM.or(0))       // Load 0 into the accumulator (chip ID)
        addInstruction(data, XCH.or(2))       // Swap accumulator with r2
        addInstruction(data, LDM.or(i.toByte()))    // Load i value into the accumulator
        addInstruction(data, SRC.or(2))       // Send address in r2,r3 to ROM/RAM
        addInstruction(data, WRR)                   // Write accumulator to ROM
    }
    addInstruction(data, JUN)  // Jump back to ROM 0
    addInstruction(data, 0x00) // Jump to address 0
    // Fill the rest of the space up till 256
    for (i in data.size until 256) {
        data.add(0)
    }
    return data
}

fun genLEDCountUsingAdd(): List<Byte> {
    val data = mutableListOf<Byte>()
    addInstruction(data, LDM.or(7))       // Load 0 into the accumulator (chip ID)
    addInstruction(data, XCH.or(2))       // Swap accumulator with r2
    addInstruction(data, LDM.or(1))       // Load 1 into the accumulator for the increment value
    addInstruction(data, XCH.or(4))       // Swap accumulator with r4
    addInstruction(data, LDM.or(0))       // Load 0 into the accumulator (initial LED value)

    val loopStart = data.size
    addInstruction(data, SRC.or(2))       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRR)                   // Write accumulator to ROM
    addInstruction(data, ADD.or(4))       // Add contents of register 4 to accumulator

    addInstruction(data, JUN)                   // Jump back to ROM 0
    addInstruction(data, loopStart.toByte())    // Jump to start of loop
    // Fill the rest of the space up till 256
    for (i in data.size until 256) {
        data.add(0)
    }
    return data
}

fun addInstruction(data: MutableList<Byte>, inst:Byte) {
    data.add(inst)
}
