package cpucore

fun handleXCH(d: Decoder) {
    // Exchange the accumulator and the scratchpad register
    if (d.clkCount.raw == 6) {
        d.setDecodedInstructionString(String.format("XCH %X", d.currInstruction.and(0xf)))
        // Output the current scratchpad register
        d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xf))
        d.writeFlag(FlagTypes.ScratchPadOut, 1)
        // Load the data into the Temp register
        d.writeFlag(FlagTypes.TempLoad, 1)
    } else if (d.clkCount.raw == 7) {
        // Output the accumulator
        d.writeFlag(FlagTypes.AccOut, 1)
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

