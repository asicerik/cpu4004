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
                d.setDecodedInstructionString("FIM")
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
        } else if (d.clkCount.raw == 7) {
            // Output the current scratchpad register
            d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xe)+1)  // Note - we are chopping bit 0
            d.writeFlag(FlagTypes.ScratchPadOut, 1)
            d.currInstruction = -1
        }
    }
}


fun handleWRR(d: Decoder) {
    // Write the accumulator to the bus
    if (d.clkCount.raw == 6) {
        d.writeFlag(FlagTypes.AccOut, 1)
        d.x3IsRead = false
        d.currInstruction = -1
    }
}

