package cpucore

fun handleFIM_SRC(d: Decoder, fullInst: Long) {
    if (fullInst.and(1) == 0L) {
        d.setDecodedInstructionString("FIM")
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
        d.currInstruction = -1
    }
}

