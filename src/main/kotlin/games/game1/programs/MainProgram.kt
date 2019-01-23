package games.game1.programs

import cpucore.*
import utils.logger

/*
    Main program pseudo code
    Inputs:
        rom0:0 = up
        rom0:1 = forward
        rom0:2 = fire
        rom1:0-3 - lfsr[0:3]    // pseudo random generator
        rom2:0-3 - lfsr[4:7]
    Outputs:
        ram0:0-3 - graphics column select [0:3]
        ram1:0   - graphics column select [4]
        ram1:1-3 - graphics row select [0:2]
        ram15:3  - pause signal
    RAM Memory:
        ram0: 0:15 - playfield ground level positions 0-15
        ram1: 0:15 - playfield ground level positions 16-31 (maybe)
        ram2: 0:15 - playfield middle level positions 0-15
        ram3: 0:15 - playfield middle level positions 16-31 (maybe)
        ram4: 0:15 - playfield top level positions 0-15
        ram5: 0:15 - playfield top level positions 16-31 (maybe)
    Registers:
        r0: player vertical position (jumping)
        r2,3: bullet horizontal position 0=not fired
        r4,5: rom/ram src registers
    main loop:
        check for key-press: up, forward, fire

        Check Jump:
            if up == true && r0 == 0 {
                r0 = 1
            }
        Handle Jump:
            if r0 != 0 {
                r0 << 1
            }

 */
class MainProgram {
    private val log = logger()
    private val data = mutableListOf<UByte>()
    // Labels are filled in on the 2nd pass
    var lbl_checkJump = 0
    var lbl_startJump = 0
    var lbl_handleJump = 0

    fun create(): MutableList<UByte> {
        // Run the generation twice so the labels/jumps get set properly
        // A simple two-pass compiler :)
        gen()
        gen()
        log.info("lbl_checkJmup={}, lbl_startJump={}, lbl_handleJump={}", lbl_checkJump, lbl_startJump, lbl_handleJump)
        return data
    }

    // this needs to be called twice to fix up the labels
    private fun gen() {
        // Build all the ROM data
        data.clear()
        mainLoop()
        checkJump()
        startJump()
        handleJump()
    }

    private fun mainLoop() {
        var page = 0
        /* PC=0 */
        // Jump to checkJump
        add(JMS, lbl_checkJump.shr(8))
        add(0U, lbl_checkJump)

        // Send the pause signal by setting bit 63 of the output bus
        add(FIM, 4)                 // Set r4
        add(0xf0U)                  // To 0xf
        add(LDM, 8)                 // Set bit 3 of the I/O bus
        add(SRC, 4)                 // Send address in r4,r5 to ROM/RAM
        add(WRR)                    // Write accumulator to ROM
        // Program will pause here

        add(JUN)                    // Go back to start
        add(0U)                     // address 0
    }

    private fun checkJump() {
        lbl_checkJump = data.size
        add(XCH, 0)                 // load r0 into accumulator
        add(JCN_ZERO_UNSET)         // If accum != 0, go to handleJump
        add(NOP, lbl_handleJump)
        // Now check the 'jump' input, rom0, bit 0
        add(FIM, 4)                 // Clear r4,r5
        add(0U)                     // FIM data (0)
        add(SRC, 4)                 // SRC command using r4,r5 as address (0)
        add(RDR)                    // Read the ROM I/O pins
        // Rotate left three times to get to just bit 0
        add(CLC)                    // Clear carry
        add(RAL)                    // Rotate left
        add(CLC)                    // Clear carry
        add(RAL)                    // Rotate left
        add(CLC)                    // Clear carry
        add(RAL)                    // Rotate left
        add(JCN_ZERO_UNSET)         // If accum != 0, go to startJump
        add(NOP, lbl_startJump)
        // No match? Go back to caller
        add(BBL, 0)
    }

    private fun startJump() {
        lbl_startJump = data.size
        // Set accumulator to the initial jump value, 0001b
        add(LDM, 1)                 // Set accum = 1
        // Continue on to handleJump
    }

    private fun handleJump() {
        lbl_handleJump = data.size
        // NOTE : the accumulator should already have the contents of r0
        // TODO: setup graphics based on initial value of accum so first pass after startJump works

        // Copy the accumulator to output bus 0
        add(FIM, 4)                 // Set r4
        add(0x0U)                   // To 0xf
        add(SRC, 4)                 // Send address in r4,r5 to ROM/RAM
        add(WMP)                    // Write accumulator to ROM

        add(CLC)                    // Clear carry
        add(RAL)                    // Rotate left
        add(XCH, 0)                 // Store the value back into r0 (could be 0, which means jump is over)
        add(BBL, 0)                 // Pop stack and go back to the caller
    }

    fun add(inst: UInt, operand: Int = 0) {
        instruction.addInstruction(data, inst, operand)
    }
}
