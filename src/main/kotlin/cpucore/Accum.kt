package cpucore

fun handleADD(d: Decoder, fullInst: Long) {
    if (d.clkCount.raw == 5) {
        d.setDecodedInstructionString(String.format("ADD %X", d.currInstruction.and(0xf)))
        // Output the data from the selected scratchpad register
        d.writeFlag(FlagTypes.IndexSelect, 17)
        d.writeFlag(FlagTypes.ScratchPadOut, 1)
    } else  if (d.clkCount.raw == 6) {
        d.setDecodedInstructionString(String.format("ADD %X", d.currInstruction.and(0xf)))
        // Output the data from the selected scratchpad register
        d.writeFlag(FlagTypes.AluMode, AluIntModeAdd)
        // Load the value into the temp register
        d.writeFlag(FlagTypes.TempLoad, 1)
    } else if (d.clkCount.raw == 7) {
        // Evaluate the ALU and output to the bus
        d.writeFlag(FlagTypes.AluEval, 1)
        d.writeFlag(FlagTypes.AluOut, 1)
    } else if (d.clkCount.raw == 0) {
        // Load the value into the accumulator
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}

fun handleSUB(d: Decoder, fullInst: Long) {
    if (d.clkCount.raw == 5) {
        d.setDecodedInstructionString(String.format("ADD %X", d.currInstruction.and(0xf)))
        // Output the data from the selected scratchpad register
        d.writeFlag(FlagTypes.IndexSelect, 17)
        d.writeFlag(FlagTypes.ScratchPadOut, 1)
    } else  if (d.clkCount.raw == 6) {
        d.setDecodedInstructionString(String.format("ADD %X", d.currInstruction.and(0xf)))
        // Output the data from the selected scratchpad register
        d.writeFlag(FlagTypes.AluMode, AluIntModeSub)
        // Load the value into the temp register
        d.writeFlag(FlagTypes.TempLoad, 1)
    } else if (d.clkCount.raw == 7) {
        // Evaluate the ALU and output to the bus
        d.writeFlag(FlagTypes.AluEval, 1)
        d.writeFlag(FlagTypes.AluOut, 1)
    } else if (d.clkCount.raw == 0) {
        // Load the value into the accumulator
        d.writeFlag(FlagTypes.AccLoad, 1)
        d.currInstruction = -1
    }
}

