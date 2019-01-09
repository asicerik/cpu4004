package cpucore

fun handleADD(d: Decoder, fullInst: Long) {
    if (d.clkCount.raw == 6) {
        d.setDecodedInstructionString(String.format("ADD %X", d.currInstruction.and(0xf)))
        d.writeFlag(FlagTypes.AluMode, AluIntModeAdd)
        // Output the data from the selected scratchpad register
        d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xf))
        d.writeFlag(FlagTypes.ScratchPadOut, 1)
    } else if (d.clkCount.raw == 7) {
        // Load the value into the temp register
        d.writeFlag(FlagTypes.TempLoad, 1)
    } else if (d.clkCount.raw == 0) {
        // Evaluate the ALU and write the value into the accumulator
        d.writeFlag(FlagTypes.AluEval, 1)
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}


