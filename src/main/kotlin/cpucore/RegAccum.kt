package cpucore

fun handleXCH(d: Decoder) {
    // Exchange the accumulator and the scratchpad register
    if (d.clkCount.raw == 5) {
        // Output the data from the selected scratchpad register
        d.writeFlag(FlagTypes.IndexSelect, 17)
        d.writeFlag(FlagTypes.ScratchPadOut, 1)
    } else if (d.clkCount.raw == 6) {
        d.setDecodedInstructionString(String.format("XCH %X", d.currInstruction.and(0xf)))
        // Load the data into the Temp register
        d.writeFlag(FlagTypes.TempLoad, 1)
        // Output the accumulator
        d.writeFlag(FlagTypes.AccOut, 1)
    } else if (d.clkCount.raw == 7) {
        // Load the data into the scratchpad register
        d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xf))
        d.writeFlag(FlagTypes.IndexLoad, 1)
    } else if (d.clkCount.raw == 0) {
        // Swap the temp and accumulator registers
        d.writeFlag(FlagTypes.AccTempSwap, 1)
        d.currInstruction = -1
    }
}

fun handleLDM(d: Decoder) {
    if (d.clkCount.raw == 5) {
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}

fun handleLD(d: Decoder) {
    // Load the accumulator with the contents of an index register
    if (d.clkCount.raw == 6) {
        d.setDecodedInstructionString(String.format("LD %X", d.currInstruction.and(0xf)))
        // Output the current scratchpad register
        d.writeFlag(FlagTypes.IndexSelect, 17)//d.currInstruction.and(0xf))
        d.writeFlag(FlagTypes.ScratchPadOut, 1)
    } else if (d.clkCount.raw == 7) {
        // Load the data into the Accumulator register
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}

fun handleINC(d: Decoder) {
    // We need to wait until clock 6 to know what to do here
    if (d.clkCount.raw == 5 && d.dblInstruction == 0) {
        d.decodeAgain = true
        return
    }
    // Increment an index register
    if (d.clkCount.raw == 6) {
        d.setDecodedInstructionString(String.format("INC %X", d.currInstruction.and(0xf)))
        // Select the scratchpad register and set the increment flag
        d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xf))
    } else if (d.clkCount.raw == 7) {
        d.writeFlag(FlagTypes.ScratchPadInc, 1)
        d.currInstruction = -1
    }
}

fun handleACC(d: Decoder) {
    // We need to wait until clock 6 to know what to do here
    if (d.clkCount.raw == 5 && d.dblInstruction == 0) {
        d.decodeAgain = true
        return
    }
    if (d.clkCount.raw == 6) {
        d.writeFlag(FlagTypes.AccInst, d.currInstruction)
        d.currInstruction = -1
    }
}



