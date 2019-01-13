package cpucore

fun handleJCN_JMS_ISZ_JUN(d: Decoder, fullInst: Long, evalResult: Boolean)  {
    val opr = d.currInstruction.and(0xf0).toByte()

    // Are we on the first phase?
    if (d.dblInstruction == 0) {
        if (opr == JCN) {
            d.writeFlag(FlagTypes.EvaluateJCN, 1)
        } else if (opr == ISZ) {
            d.writeFlag(FlagTypes.EvaluateISZ, 1)
        }
        if (d.clkCount.raw == 6) {
            if (opr == JCN) {
                d.setDecodedInstructionString(String.format("JCN %X", d.currInstruction.and(0xf)))
            } else if (opr == JUN) {
                d.setDecodedInstructionString(String.format("JUN %X", d.currInstruction.and(0xf)))
            } else if (opr == JMS) {
                d.setDecodedInstructionString(String.format("JMS %X", d.currInstruction.and(0xf)))
            } else if (opr == ISZ) {
                d.setDecodedInstructionString(String.format("ISZ %X", d.currInstruction.and(0xf)))
            }
            if (opr == ISZ) {
                d.writeFlag(FlagTypes.IndexSelect, (d.currInstruction.and(0xf)))
                d.writeFlag(FlagTypes.ScratchPadInc, 1)
            } else {
                // Store the upper 4 bits in the temp register
                d.writeFlag(FlagTypes.TempLoad, 1)
            }
            d.dblInstruction = d.currInstruction
            d.currInstruction = -1
        }
    } else {
        if (d.clkCount.raw == 5) {
            // If this is a conditional jump, evaluate the condition here
            var blockJump = false
            if (opr == JCN || opr == ISZ) {
                blockJump = !evalResult
            }
            if (!blockJump) {
                // Block the PC increment
                d.inhibitPCInc = true
                // Output the lower 4 bits of the instruction register
                // It contains the lowest 4 bits of the address
                d.writeFlag(FlagTypes.InstRegOut, 1)
            } else {
                d.log.info("Conditional jump was not taken")
                d.dblInstruction = 0
                d.currInstruction = -1
            }
            if (opr == JMS) {
                // Push the current address onto the stack
                d.writeFlag(FlagTypes.StackPush, 1)
            }
        } else if (d.clkCount.raw == 6) {
            // Load the lowest 4 bits into the PC
            d.writeFlag(FlagTypes.PCLoad, 1)
            // Output the higher 4 bits of the instruction register
            // It contains the middle 4 bits of the address
            d.writeFlag(FlagTypes.InstRegOut, 2)
        } else if (d.clkCount.raw == 7) {
            // Load the middle 4 bits into the PC
            d.writeFlag(FlagTypes.PCLoad, 2)
            // Output the temp register
            // It contains the middle 4 bits of the address
            d.writeFlag(FlagTypes.TempOut, 1)
        } else if (d.clkCount.raw == 0) {
            if (opr == JUN || opr == JMS) {
                // NOTE: we have already started outputting the PC onto the bus
                // for the next cycle, but we can still update the highest bits
                // since they go out last
                // Load the highest 4 bits into the PC
                d.writeFlag(FlagTypes.PCLoad, 3)
            }
            d.dblInstruction = 0
            d.currInstruction = -1
            // Unblock the PC increment
            d.inhibitPCInc = false
        }
    }
}

// If these functions return false, conditional jumps are blocked
fun evalulateJCN(c: CpuCore): Boolean {
    // Not sure how the real CPU does this, so I am cutting corners here
    var condititonFlags = c.aluCore.readTempDirect()
    var aluFlags = c.aluCore.getFlags()
    var testBitFlag     = condititonFlags.toInt().and(0x1)
    var carryBitFlag    = condititonFlags.shr(1).toInt().and(0x1)
    var zeroBitFlag     = condititonFlags.shr(2).toInt().and(0x1)
    var invertBitFlag   = condititonFlags.shr(3).toInt().and(0x1)
    var result = true
    if (invertBitFlag == 0) {
        result = ((carryBitFlag == 0) || (aluFlags.carry == 1)) &&
                 ((zeroBitFlag == 0) || (aluFlags.zero == 1))
    } else {
        result = ((carryBitFlag == 1) && (aluFlags.carry == 0)) ||
                ((zeroBitFlag == 1) && (aluFlags.zero == 0))
    }
    c.log.debug(String.format("evalulateJCN: conditionalFlags=%X, aluFlags=%b. Result=%b", condititonFlags, aluFlags, result))
    return result
}

fun evalulateISZ(c: CpuCore): Boolean {
    var condition = c.indexRegisters.isRegisterZero()
    c.log.debug(String.format("evalulateISZ: Result=%b", condition))
    return !condition
}

fun handleFIN_JIN(d: Decoder, fullInst: Long) {
    // We need to wait until clock 6 to know what to do here
    if (d.clkCount.raw == 5 && d.dblInstruction == 0) {
        d.decodeAgain = true
        return
    }

    if (fullInst.and(0xf1) == JIN.toLong()) {
        // Jump indirect to address in specified register pair
        if (d.clkCount.raw == 6) {
            d.setDecodedInstructionString(String.format("JIN %X", d.currInstruction.and(0xe)))
            // Output the lower address to the program counter
            d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xe)+1) // Note - we are chopping bit 0
            d.writeFlag(FlagTypes.ScratchPadOut, 1)
            // Block the PC increment
            d.inhibitPCInc = true
        } else if (d.clkCount.raw == 7) {
            // Load the lowest 4 bits into the PC
            d.writeFlag(FlagTypes.PCLoad, 1)

            // Output the lower address to the program counter
            d.writeFlag(FlagTypes.IndexSelect, d.currInstruction.and(0xe)+0) // Note - we are chopping bit 0
            d.writeFlag(FlagTypes.ScratchPadOut, 1)
        } else if (d.clkCount.raw == 0) {
            // Load the middle 4 bits into the PC
            d.writeFlag(FlagTypes.PCLoad, 2)
            d.currInstruction = -1
            // Unblock the PC increment
            d.inhibitPCInc = false
        }
    } else if (fullInst.and(0xf1) == FIN.toLong()) {
        // Fetch indirect to address in register pair 0
        // then store the result in specified register pair
        if (d.clkCount.raw == 0) {
            // Output the lower address to the data bus
            // According to the datasheet, the odd register contains the lower 4 bits of address
            d.writeFlag(FlagTypes.IndexSelect, 1)
            d.writeFlag(FlagTypes.ScratchPadOut, 1)
            // UnBlock the PC increment
            d.inhibitPCInc = false
            // Disable the program counter from using the bus
            d.inhibitPC = true
            // Mark this as a double instruction to prevent the instruction register
            // from being clobbered
            d.dblInstruction = d.currInstruction
        } else if (d.clkCount.raw == 1) {
            // Output the middle address to the data bus
            // According to the datasheet, the even register contains the upper 4 bits of address
            d.writeFlag(FlagTypes.IndexSelect, 0)
            d.writeFlag(FlagTypes.ScratchPadOut, 1)
        } else if (d.clkCount.raw == 2) {
            // Unblock the PC
            d.inhibitPC = false
        } else if (d.clkCount.raw == 3 && d.dblInstruction > 0) {
            d.setDecodedInstructionString(String.format("FIN %X", d.currInstruction.and(0xf)))
            // Load the ROM data into the scratch pad register pair 0
            d.writeFlag(FlagTypes.IndexSelect, d.dblInstruction.and(0xe)+0) // Note - we are chopping bit 0
        } else if (d.clkCount.raw == 4 && d.dblInstruction > 0) {
            // Load the ROM data into the scratch pad register pair 1
            d.writeFlag(FlagTypes.IndexSelect, d.dblInstruction.and(0xe)+1) // Note - we are chopping bit 0
            d.writeFlag(FlagTypes.IndexLoad, 1)
        } else if (d.clkCount.raw == 5 && d.dblInstruction > 0) {
            d.writeFlag(FlagTypes.IndexLoad, 1)
            // Done
            d.dblInstruction = 0
            d.currInstruction = -1
        } else if (d.clkCount.raw == 6 && d.dblInstruction <= 0) {
            // Block the PC increment
            d.inhibitPCInc = true
        }
    }
}
