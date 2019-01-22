package instruction

import cpucore.*

fun genLEDCount(): List<UByte> {
    val data = mutableListOf<UByte>()
    for (i in 0 until 16) {
        addInstruction(data, LDM, 2)       // Load 0 into the accumulator (chip ID)
        addInstruction(data, XCH, 2)       // Swap accumulator with r2
        addInstruction(data, LDM, i)               // Load i value into the accumulator
        addInstruction(data, SRC, 2)       // Send address in r2,r3 to ROM/RAM
        addInstruction(data, WRR)                  // Write accumulator to ROM
    }
    addInstruction(data, JUN, 0)           // Jump back to ROM 0
    addInstruction(data, 0U, 0)       // Jump to address 0
    // Fill the rest of the space up till 256
    fillEmptyProgramData(data)
    return data
}

fun genLEDCountUsingAdd(): List<UByte> {
    val data = mutableListOf<UByte>()
    addInstruction(data, LDM, 0)       // Load 0 into the accumulator (chip ID)
    addInstruction(data, XCH, 2)       // Swap accumulator with r2
    addInstruction(data, LDM, 1)       // Load 1 into the accumulator for the increment value
    addInstruction(data, XCH, 4)       // Swap accumulator with r4
    addInstruction(data, LDM, 0)       // Load 0 into the accumulator (initial LED value)

    val loopStart = data.size
    addInstruction(data, SRC, 2)       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRR, 0)       // Write accumulator to ROM
    addInstruction(data, ADD, 4)       // Add contents of register 4 to accumulator
    addInstruction(data, SRC, 2)       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WMP)                  // Write accumulator to ROM
    addInstruction(data, SRC, 2)       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRM)                  // Write accumulator to RAM memory

    // Store the accumulator into r4
    addInstruction(data, XCH, 4)       // Swap accumulator with r4

    // Set bit 63 of the i/o bus to indicate cycle is complete
    addInstruction(data, LDM, 15)      // Load 0 into the accumulator (chip ID)
    addInstruction(data, XCH, 2)       // Swap accumulator with r2
    addInstruction(data, LDM, 8)       // Load 8 into the accumulator (set bit 3 of the i/o bus)
    addInstruction(data, SRC, 2)       // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRR)                  // Write accumulator to ROM

    // Restore r2/r3 back to ROM 0
    addInstruction(data, LDM, 0)       // Load 0 into the accumulator (chip ID)
    addInstruction(data, XCH, 2)       // Swap accumulator with r2

    // Restore the accumulator
    addInstruction(data, XCH, 4)       // Swap accumulator with r4

    addInstruction(data, JUN)                  // Jump back to ROM 0
    addInstruction(data, loopStart.toUInt())   // Jump to start of loop
    // Fill the rest of the space up till 256
    fillEmptyProgramData(data)
    return data
}

fun genShifter(steps: Int): List<UByte> {
    // r0 contains 0
    // r2/r3 contain the current chip ID
    // r4 contains the shift value
    // r5, RAM status character 0 contains the loop counter
    // r8 contains the chip ID for cycle done message
    val data = mutableListOf<UByte>()
    /* PC=0 */
    addInstruction(data, LDM, 15)      // Load 15 into the accumulator (chip ID)
    addInstruction(data, XCH, 8)       // Swap accumulator with r8

    // Outer loop start
    val start = data.size
    /* PC=2 */
    addInstruction(data, LDM, 0)        // Load 0 into the accumulator (chip ID)
    addInstruction(data, XCH, 2)        // Swap accumulator with r2

    // Set loop counter
//    addInstruction(data, LDM, 16-steps)// Load starting loop counter
    /* PC=4 */
    addInstruction(data, LDM, 16-steps) // Load starting loop counter
    addInstruction(data, SRC, 0)        // Send address in r0,r1 to ROM/RAM
    addInstruction(data, WR0, 0)        // Write loop count to RAM status char 0

    addInstruction(data, LDM, 1)        // Load 0 into the accumulator (initial shift value)

    // Inner loop start
    val loopStart = data.size
    /* PC=8 */
    addInstruction(data, SRC, 2)        // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRR, 0)        // Write accumulator to ROM
    addInstruction(data, RAL, 0)        // Shift accumulator 1 to the left

    // Store the accumulator into r4
    addInstruction(data, XCH, 4)        // Swap accumulator with r4

    // Set bit 63 of the i/o bus to indicate cycle is complete
    /* PC=12 */
    addInstruction(data, LDM, 8)        // Load 8 into the accumulator (set bit 3 of the i/o bus)
    addInstruction(data, SRC, 8)        // Send address in r8,r9 to ROM/RAM
    addInstruction(data, WRR)                  // Write accumulator to ROM
    // Program will pause here

    // Restore the accumulator from r4
    /* PC=15 */
    addInstruction(data, XCH, 4)        // Swap accumulator with r4
    addInstruction(data, JCN, 4)        // Test if acc = 0

    val jumpLoc = data.size                     // Remember this location so we can fix it later
    addInstruction(data, NOP, 0)        // Jump to loop check if acc=0

    // No jump, so we still have shifting to do
    /* PC=18 */
    addInstruction(data, JUN)                   // Jump back to inner loop start
    addInstruction(data, loopStart.toUInt())    // Jump to start of loop

    // Loop check
    data[jumpLoc] = data.size.toUByte()         // Set the previous jump loc since we know it now

    /* PC=20 */
    addInstruction(data, CLC)                   // Clear the carry bit
    addInstruction(data, LDM, 0)        // Clean the current I/O bus
    addInstruction(data, SRC, 2)        // Send address in r2,r3 to ROM/RAM
    addInstruction(data, WRR, 0)        // Write accumulator to ROM

    /* PC=24 */
    addInstruction(data, SRC, 0)        // Send address in r0,r1 to ROM/RAM
    addInstruction(data, RD0, 0)        // Read loop count from RAM status char 0
    addInstruction(data, XCH, 5)        // Swap r5 and acc
    addInstruction(data, INC, 5)        // Inc r5. When it reaches 0, done
    addInstruction(data, XCH, 5)        // Swap r5 and acc
    addInstruction(data, JCN, 4)        // Test if acc = 0
    addInstruction(data, NOP, start)            // Jump back to start if acc=0

    /* PC=28 */
// No jump, write new loop count to RAM
    addInstruction(data, SRC, 0)        // Send address in r0,r1 to ROM/RAM
    addInstruction(data, WR0, 0)        // Write loop count to RAM status char 0
    // Increment r2 (Chip ID)
    addInstruction(data, INC, 2)       // Inc r2, our chip ID

    addInstruction(data, LDM, 1)        // Load 0 into the accumulator (initial shift value)

    addInstruction(data, JUN)                   // Jump back to start of inner loop
    addInstruction(data, loopStart.toUInt())    // Jump to start of loop
    // Fill the rest of the space up till 256
    fillEmptyProgramData(data)
    return data
}


//fun genTestAND(): List<Byte> {
//    val data = mutableListOf<Byte>()
//    val loopStart = data.size
//    addInstruction(data, FIM.or(0))     // Store into reg pair r0,r1
//    addInstruction(data, 0x5C)           // Store "AND" 5 and C
//    val jumpLoc = data.size + 4
//    addInstruction(data, JMS.or(0))     // Jump to the "AND" routine
//    addInstruction(data, jumpLoc.toByte())
//    addInstruction(data, JUN)                   // Jump back to ROM 0
//    addInstruction(data, loopStart.toByte())    // Jump to start of loop
//    genAND(data)
//    // Fill the rest of the space up till 256
//    fillEmptyProgramData(data)
//    return data
//}
//
//fun genAND(data: MutableList<Byte>) {
//    addInstruction(data, FIM.or(2))     // Store into reg pair r2,r3
//    addInstruction(data, 0x0B)           // Store 11 into r3
//    val loopStart = data.size
//    addInstruction(data, LDM.or(0))     // Put 0 in acc
//    addInstruction(data, XCH.or(0))     // Swap r0 and acc. r0=0
//    addInstruction(data, RAL)                 // 1st "AND" bit to carry
//    addInstruction(data, XCH.or(0))     // Put shifted data into r0. acc=0
//    addInstruction(data, INC.or(3))     // Inc r3. When it reaches 0, done
//    addInstruction(data, XCH.or(3))     // Swap r3 and acc
//    addInstruction(data, JCN.or(4))     // Test if acc = 0
//    val jumpLoc = data.size
//    addInstruction(data, NOP)                 // Jump address goes here later
//    addInstruction(data, XCH.or(3))     // No jump, restore l3 and acc
//    addInstruction(data, RAR)                 // Remaining highest bit of r0 in acc
//    addInstruction(data, XCH.or(2))     // Save this into r2
//    addInstruction(data, XCH.or(1))     // Get bit of r1
//    addInstruction(data, RAL)                 // Highest bit to carry
//    addInstruction(data, XCH.or(1))     // Put shifted data into r1
//    addInstruction(data, RAR)                 // 2nd "AND" bit to acc
//    addInstruction(data, ADD.or(2))     // "AND" result is now in carry
//    // Jump back to loopstart
//    addInstruction(data, JUN.or(loopStart.shr(8).and(0xf).toByte()))
//    addInstruction(data, loopStart.and(0xff).toByte())
//    val loopEnd = data.size
//    addInstruction(data, BBL.or(0))     // Return to caller
//    // Go fix the jump location
//    data[jumpLoc] = loopEnd.and(0xff).toByte()
//}
//

fun fillEmptyProgramData(data: MutableList<UByte>) {
    for (i in data.size until 256) {
        data.add(0U)
    }
}

fun addInstruction(data: MutableList<UByte>, inst: UInt, operand: Int = 0) {
    data.add(inst.or(operand.toUInt()).toUByte())
}
