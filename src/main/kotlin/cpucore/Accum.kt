package cpucore

fun handleADD(d: Decoder, fullInst: Long) {
    if (d.clkCount.raw == 5) {
        d.setDecodedInstructionString(String.format("ADD %X", d.currInstruction.and(0xf)))
        // Output the data from the selected scratchpad register
//        d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xf))
        d.writeFlag(FlagTypes.AluMode, AluIntModeAdd)
        d.writeFlag(FlagTypes.IndexSelect, 17)
        d.writeFlag(FlagTypes.ScratchPadOut, 1)
        // Load the value into the temp register
        d.writeFlag(FlagTypes.TempLoad, 1)
    } else if (d.clkCount.raw == 6) {
        // Load the value into the temp register
//        d.writeFlag(FlagTypes.TempLoad, 1)
        d.writeFlag(FlagTypes.AluEval, 1)
    } else if (d.clkCount.raw == 7) {
        // Evaluate the ALU and write the value into the accumulator
        d.writeFlag(FlagTypes.AluOut, 1)
    } else if (d.clkCount.raw == 0) {
        // Evaluate the ALU and write the value into the accumulator
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}


