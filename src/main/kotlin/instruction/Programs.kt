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
    fillEmptyProgramData(data)
    return data
}

fun genLEDCountUsingAdd(): List<Byte> {
    val data = mutableListOf<Byte>()
    addInstruction(data, LDM.or(0))       // Load 0 into the accumulator (chip ID)
    addInstruction(data, XCH.or(2))       // Swap accumulator with r2
    addInstruction(data, LDM.or(1))       // Load 1 into the accumulator for the increment value
    addInstruction(data, XCH.or(4))       // Swap accumulator with r4
    addInstruction(data, LDM.or(0))       // Load 0 into the accumulator (initial LED value)

    val loopStart = data.size
    addInstruction(data, SRC.or(2))       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRR)                   // Write accumulator to ROM
    addInstruction(data, ADD.or(4))       // Add contents of register 4 to accumulator
    addInstruction(data, SRC.or(2))       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WMP)                   // Write accumulator to ROM
    addInstruction(data, SRC.or(2))       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRM)                   // Write accumulator to RAM memory

    addInstruction(data, JUN)                   // Jump back to ROM 0
    addInstruction(data, loopStart.toByte())    // Jump to start of loop
    // Fill the rest of the space up till 256
    fillEmptyProgramData(data)
    return data
}

fun genTestAND(): List<Byte> {
    val data = mutableListOf<Byte>()
    val loopStart = data.size
    addInstruction(data, FIM.or(0))     // Store into reg pair r0,r1
    addInstruction(data, 0x5C)           // Store "AND" 5 and C
    val jumpLoc = data.size + 4
    addInstruction(data, JMS.or(0))     // Jump to the "AND" routine
    addInstruction(data, jumpLoc.toByte())
    addInstruction(data, JUN)                   // Jump back to ROM 0
    addInstruction(data, loopStart.toByte())    // Jump to start of loop
    genAND(data)
    // Fill the rest of the space up till 256
    fillEmptyProgramData(data)
    return data
}

fun genAND(data: MutableList<Byte>) {
    addInstruction(data, FIM.or(2))     // Store into reg pair r2,r3
    addInstruction(data, 0x0B)           // Store 11 into r3
    val loopStart = data.size
    addInstruction(data, LDM.or(0))     // Put 0 in acc
    addInstruction(data, XCH.or(0))     // Swap r0 and acc. r0=0
    addInstruction(data, RAL)                 // 1st "AND" bit to carry
    addInstruction(data, XCH.or(0))     // Put shifted data into r0. acc=0
    addInstruction(data, INC.or(3))     // Inc r3. When it reaches 0, done
    addInstruction(data, XCH.or(3))     // Swap r3 and acc
    addInstruction(data, JCN.or(4))     // Test if acc = 0
    val jumpLoc = data.size
    addInstruction(data, NOP)                 // Jump address goes here later
    addInstruction(data, XCH.or(3))     // No jump, restore l3 and acc
    addInstruction(data, RAR)                 // Remaining highest bit of r0 in acc
    addInstruction(data, XCH.or(2))     // Save this into r2
    addInstruction(data, XCH.or(1))     // Get bit of r1
    addInstruction(data, RAL)                 // Highest bit to carry
    addInstruction(data, XCH.or(1))     // Put shifted data into r1
    addInstruction(data, RAR)                 // 2nd "AND" bit to acc
    addInstruction(data, ADD.or(2))     // "AND" result is now in carry
    // Jump back to loopstart
    addInstruction(data, JUN.or(loopStart.shr(8).and(0xf).toByte()))
    addInstruction(data, loopStart.and(0xff).toByte())
    val loopEnd = data.size
    addInstruction(data, BBL.or(0))     // Return to caller
    // Go fix the jump location
    data[jumpLoc] = loopEnd.and(0xff).toByte()
}


fun fillEmptyProgramData(data: MutableList<Byte>) {
    for (i in data.size until 256) {
        data.add(0)
    }
}

fun addInstruction(data: MutableList<Byte>, inst: Byte) {
    data.add(inst)
}
