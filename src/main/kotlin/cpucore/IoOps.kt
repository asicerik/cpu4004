package cpucore

fun handleFIM_SRC(d: Decoder, fullInst: Long) {
    // We need to wait until clock 6 to know what to do here
    if (d.clkCount.raw == 5 && d.dblInstruction == 0) {
        d.decodeAgain = true
        return
    }
    if (fullInst.and(1) == 0L) {
        // Load the index registers with the contents of the selected ROM address
        if (d.dblInstruction == 0) {
            if (d.clkCount.raw == 6) {
                d.setDecodedInstructionString("FIM")
                // Select the first index register
                d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xe))  // Note - we are chopping bit 0
                d.dblInstruction = d.currInstruction
            }
        } else {
            if (d.clkCount.raw == 4) {
                // Write the index register
                d.writeFlag(FlagTypes.IndexLoad, 1)
                // Select the second index register
                d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xe)+1)  // Note - we are chopping bit 0
            } else if (d.clkCount.raw == 5) {
                // Write the index register
                d.writeFlag(FlagTypes.IndexLoad, 1)
                d.dblInstruction = 0
                d.currInstruction = -1
            }
        }
    } else {
        // Load the accumulator with the contents of an index register
        if (d.clkCount.raw == 6) {
            d.setDecodedInstructionString(String.format("SRC %X", d.currInstruction.and(0xf).shr(1)))
            // Output the current scratchpad register
            d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xe))  // Note - we are chopping bit 0
            d.writeFlag(FlagTypes.ScratchPadOut, 1)
            // Assert the CMROM/RAM lines for an upcoming i/o operation
            d.writeFlag(FlagTypes.CmRom, 1)
            d.writeFlag(FlagTypes.CmRam, 1)
        } else if (d.clkCount.raw == 7) {
            // Output the current scratchpad register
            d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xe)+1)  // Note - we are chopping bit 0
            d.writeFlag(FlagTypes.ScratchPadOut, 1)
            d.currInstruction = -1
        }
    }
}

// WRR, WMP, WR0-3 all do the same thing as far as the processor is concerned
fun handleWRM_WMP_WRR_WRn(d: Decoder) {
    when (d.currInstruction.toUInt()) {
        WRR -> { d.setDecodedInstructionString("WRR") }
        WMP -> { d.setDecodedInstructionString("WMP") }
        WRM -> { d.setDecodedInstructionString("WRM") }
        WR0 -> { d.setDecodedInstructionString("WR0") }
        WR1 -> { d.setDecodedInstructionString("WR1") }
        WR2 -> { d.setDecodedInstructionString("WR2") }
        WR3 -> { d.setDecodedInstructionString("WR3") }
    }
    // Write the accumulator to the bus
    if (d.clkCount.raw == 6) {
        d.writeFlag(FlagTypes.AccOut, 1)

        d.x3IsRead = false
        d.currInstruction = -1
    }
}

// RDM, RDR, RD0-3 all do the same thing as far as the processor is concerned
fun handleRDM_RDR_RDn(d: Decoder) {
    // Write the value from the bus to the accumulator
    if (d.clkCount.raw == 7) {
        d.writeFlag(FlagTypes.AccLoad, 1)

        d.currInstruction = -1
    }
}

fun handleSBM(d: Decoder) {
    // Subtract the value from the RAM (on the bus) from the accumulator
    if (d.clkCount.raw == 7) {
        d.writeFlag(FlagTypes.TempLoad, 1)
        d.writeFlag(FlagTypes.AluMode, AluIntModeSub)
        // Evaluate the ALU and output to the bus
        d.writeFlag(FlagTypes.AluEval, 1)
        d.writeFlag(FlagTypes.AluOut, 1)
    } else if (d.clkCount.raw == 0) {
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}

fun handlADM(d: Decoder) {
    // Add the value from the RAM (on the bus) to the accumulator
    if (d.clkCount.raw == 7) {
        d.writeFlag(FlagTypes.TempLoad, 1)
        d.writeFlag(FlagTypes.AluMode, AluIntModeAdd)
        // Evaluate the ALU and output to the bus
        d.writeFlag(FlagTypes.AluEval, 1)
        d.writeFlag(FlagTypes.AluOut, 1)
    } else if (d.clkCount.raw == 0) {
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}
