package cpucore

import org.assertj.core.api.Assertions.assertThat

fun step(count: Int) {
    for (i in 0 until count) {
        emitter!!.onNext(0)
        emitter!!.onNext(1)
    }
}

fun waitForSync(core: CpuCore): Pair<Boolean, Int> {
    var count = 0
    var syncSeen = false
    for (i in 0..15) {
        step(1)
        if (core.sync.clocked == 0) {
            if (!syncSeen) {
                syncSeen = true
                count = 0
            } else {
                // Run one extra clock to put us on cycle 0
                step(1)
                break
            }
        } else {
            count++
        }
    }
    return Pair(syncSeen, count)
}

fun runOneCycle(core: CpuCore, inst: UInt, operand: Int): ULong {
    val res = runOneIOCycle(core, inst, operand)
    return res.first
}

fun runOneIOCycle(core: CpuCore, inst: UInt, operand: Int): Pair<ULong, ULong> {
    var addr = 0UL
    var ioVal = 0UL
    var data = inst.or(operand.toUInt()).toULong()
    for (i in 0..7) {
        emitter!!.onNext(0)

        if (i < 3) {
            addr = addr.or(core.extDataBus.read().shl(i * 4))
        }
        if (i == 6) {
            ioVal = core.extDataBus.read().shl(4)
        }
        if (i == 7) {
            ioVal = ioVal.or(core.extDataBus.read())
        }
        // Check for the presence/lack of CM_ROM/RAM
        var expCmRam = 0xE  // CMRAM0 (active low)
        if (core.aluCore.currentRamBank > 0U) {
            expCmRam = core.aluCore.currentRamBank.inv().and(0xfU).toInt()
        }
        if (i == 2) {
            // Cycle 2 is the ROM instruction read, so CMROM should always be asserted
            assertThat(core.cmRom.clocked).isEqualTo(0)
            assertThat(core.cmRam.clocked).isEqualTo(0xf)
        } else if (i == 4 && (data.and(0xf0U).toUInt() == IO)) {
            // Cycle 4 on IO ops should have the signals asserted
            assertThat(core.cmRom.clocked).isEqualTo(0)
            assertThat(core.cmRam.clocked).isEqualTo(expCmRam)
        } else if (i == 6) {
            if (data.and(0xf0U).toUInt() == SRC) {
                assertThat(core.cmRom.clocked).isEqualTo(0)
                assertThat(core.cmRam.clocked).isEqualTo(expCmRam)
            }
        } else {
            assertThat(core.cmRom.clocked).isEqualTo(1)
            assertThat(core.cmRam.clocked).isEqualTo(0xf)
        }
        emitter!!.onNext(1)
        if (i == 2) {
            core.extDataBus.write(data.shr(4).and(0xfU))
        } else if (i == 3) {
            core.extDataBus.write(data.and(0xfU))
        }
    }
    return Pair(addr, ioVal)
}

fun runOneIOReadCycle(core: CpuCore, inst: UInt, operand: Int, data: ULong): ULong {
    var addr = 0UL
    val fullInst = inst.or(operand.toUInt()).toULong()
    for (i in 0..7) {
        emitter!!.onNext(0)

        if (i < 3) {
            addr = addr.or(core.extDataBus.read().shl(i * 4))
        }
        // Check for the presence/lack of CM_ROM/RAM
        var expCmRam = 0xE  // CMRAM0 (active low)
        if (core.aluCore.currentRamBank > 0U) {
            expCmRam = core.aluCore.currentRamBank.inv().and(0xfU).toInt()
        }
        if (i == 2) {
            // Cycle 2 is the ROM instruction read, so CMROM should always be asserted
            assertThat(core.cmRom.clocked).isEqualTo(0)
            assertThat(core.cmRam.clocked).isEqualTo(0xf)
        } else if (i == 4 && (inst.and(0xf0U).toUInt() == IO)) {
            // Cycle 4 on IO ops should have the signals asserted
            assertThat(core.cmRom.clocked).isEqualTo(0)
            assertThat(core.cmRam.clocked).isEqualTo(expCmRam)
        } else if (i == 6) {
            if (inst.and(0xf0U).toUInt() == SRC) {
                assertThat(core.cmRom.clocked).isEqualTo(0)
                assertThat(core.cmRam.clocked).isEqualTo(expCmRam)
            }
        } else {
            assertThat(core.cmRom.clocked).isEqualTo(1)
            assertThat(core.cmRam.clocked).isEqualTo(0xf)
        }
        emitter!!.onNext(1)
        if (i == 2) {
            core.extDataBus.write(fullInst.shr(4).and(0xfU))
        } else if (i == 3) {
            core.extDataBus.write(fullInst.and(0xfU))
        } else if (i == 5) {
            // IO read data to the CPU
            core.extDataBus.write(data.and(0xfU))
        }
    }
    return addr
}


fun loadRegisterPair(core: CpuCore, data: ULong, regPair: Int) : ULong {
    // Load the accumulator with the lower 4 bits
    var nextAddr = runOneCycle(core, LDM, data.and(0xfU).toInt())
    // Swap the accumulator with the odd register
    nextAddr = runOneCycle(core, XCH, regPair.shl(1)+1)
    // Load the accumulator with the higher 4 bits
    nextAddr = runOneCycle(core, LDM, data.shr(4).and(0xfU).toInt())
    // Swap the accumulator with the even register
    nextAddr = runOneCycle(core, XCH, regPair.shl(1))
    return nextAddr
}


fun verifyJump(core: CpuCore, instruction: UInt, jumpExpected: Boolean) {
    verifyJumpExtended(core, instruction, jumpExpected, false)
}

fun verifyJumpExtended(core: CpuCore, instruction: UInt, jumpExpected: Boolean, extendedAddress: Boolean) {
    var addr = 0UL
    var data = 0UL
    var jumpAddress = 0xabcU
    var nextAddr = runOneCycle(core, NOP, 0) + 1U
    // run 5 complete cycles
    for (i in 0..4) {
        when (i) {
            0,1,4 -> {
                data = 0x0U
            }
            2 -> {
                if (extendedAddress) {
                    data = instruction.or(jumpAddress.shr(8)).toULong()
                } else {
                    data = instruction.toULong()
                }
            }
            3 -> {
                if (extendedAddress) {
                    data = jumpAddress.toULong()
                } else {
                    data = jumpAddress.and(0xffU).toULong()
                }
            }
        }
        addr = runOneCycle(core, data.toUInt(), 0)
        assertThat(nextAddr).isEqualTo(addr)
        if (i == 3 && jumpExpected) {
            nextAddr = data
        } else {
            nextAddr++
        }
    }

}


