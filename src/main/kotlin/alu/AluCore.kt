package alu

import common.Bus
import common.BusWidth
import common.Maskable
import common.Register
import cpucore.*
import io.reactivex.Observable
import utils.logger
import kotlin.experimental.and

class AluCore(val dataBus: Bus, clk: Observable<Int>) {
    val log = logger()

    val alu = Alu(BusWidth, dataBus, clk)
    val accum = Register(0U, clk)
    val temp = Register(0U, clk)
    val flags = Register(0U, clk)
    val accumBus = Bus()
    val tempBus = Bus()
    val flagsBus = Bus()
    val mode = ""
    var currentRamBank = 1U
        private set
    var accumDrivingBus = false
    var tempDrivingBus  = false
    var flagsDrivingBus = false
    var aluDrivingBus   = false

    init {
        accum.init(dataBus, BusWidth, "ACC ")
        temp.init(dataBus, BusWidth, "TEMP ")
        flags.init(dataBus, BusWidth, "FLAG ")
        accumBus.init(BusWidth, "")
        tempBus.init(BusWidth, "")
        flagsBus.init(BusWidth, "")
        updateFlags()
    }

    fun reset() {
        accum.reset()
        temp.reset()
        flags.reset()
        alu.reset()
        updateFlags()
        currentRamBank = 1U
    }

    fun swap() {
        val tmp = temp.readDirect()
        temp.writeDirect(accum.readDirect())
        accum.writeDirect(tmp)
        log.debug(String.format("ALU Swap. accum=%X, temp=%X", accum.readDirect().toLong(), temp.readDirect().toLong()))
    }

    fun writeAccumulator() {
        accum.write()
        log.debug(String.format("ACCUM write with %X", dataBus.read().toLong()))
    }

    fun writeTemp() {
        temp.write()
        log.debug(String.format("Temp write with %X", dataBus.read().toLong()))
    }

    fun readAccumulator() {
        accum.read()
    }

    fun readTemp() {
        temp.read()
    }

    fun readTempDirect(): ULong {
        return temp.readDirect()
    }

    fun readFlags() {
        flags.read()
        flagsDrivingBus = true
    }

    fun readFlagsDirect(): ULong {
        return flags.readDirect()
    }

    fun setMode(mode: Int) {
        when (mode) {
            AluIntModeNone -> {
                alu.setAluMode(AluNone)
            }
            AluIntModeAdd -> {
                alu.setAluMode(AluAdd)
            }
            AluIntModeSub -> {
                alu.setAluMode(AluSub)
            }
            else -> {
                log.warn(String.format("** Invalid ALU mode %d", mode))
            }
        }
    }

    fun evaluate() {
        alu.evaluate(accum.readDirect(), temp.readDirect())
    }

    fun readEval() {
        //alu.readOutput()
        aluDrivingBus = true
    }

    fun getFlags(): AluFlags {
        val flagsRaw = updateFlags()
        val flagsVal = AluFlags(0,0)
        if (flagsRaw.and(FlagPosZero) != 0UL) {
            flagsVal.zero = 1
        }
        if (flagsRaw.and(FlagPosCarry) != 0UL) {
            flagsVal.carry = 1
        }
        return flagsVal
    }

    fun updateFlags(): ULong {
        val accum = accum.readDirect()
        var flagsVal = flags.readDirect()
        if (accum == 0UL) {
            flagsVal = flagsVal.or(FlagPosZero)
        } else {
            flagsVal = flagsVal.and(FlagPosZero.inv())
        }
        if (alu.carry != 0UL) {
            flagsVal = flagsVal.or(FlagPosCarry)
        } else {
            flagsVal = flagsVal.and(FlagPosCarry.inv())
        }
        flags.writeDirect(flagsVal)
        return flagsVal
    }

    fun exectuteAccInst(inst: UInt) {
        var accumPre = accum.readDirect()
        var carryPre = getFlags().carry
        // All this stuff is NOT cycle accurate. Who knows how this works
        // in the read CPU. Probably not this way though :)
        when (inst) {
            CLB -> {
                accum.writeDirect(0U)
                alu.setCarryVal(0U)
            }
            CLC -> {
                alu.setCarryVal(0U)
            }
            IAC -> {
                alu.setAluMode(AluAdd)
                alu.evaluate(accum.readDirect(), 1U)
                accum.writeDirect(alu.value)
            }
            CMC -> {
                alu.complimentCarry()
            }
            CMA -> {
                accum.writeDirect(accum.readDirect().inv().and(alu.mask))
            }
            RAL -> {
                val flags  = getFlags()
                var accumVal = accum.readDirect()
                accumVal = accumVal.shl(1)
                // The high bit becomes the carry bit
                if (accumVal.and(alu.carryMask) != 0UL) {
                    alu.setCarryVal(1U)
                } else {
                    alu.setCarryVal(0U)
                }
                // The low bit is the previous carry
                if (flags.carry != 0) {
                    accumVal = accumVal.or(1U)
                }
                accum.writeDirect(accumVal)
            }
            RAR -> {
                val flags  = getFlags()
                var accumVal = accum.readDirect()
                val lsb = accumVal.and(0x1U)
                accumVal = accumVal.shr(1)
                // Set the carry to the lsb before the shift
                alu.setCarryVal(lsb)
                // The high bit is the previous carry
                if (flags.carry != 0) {
                    accumVal = accumVal.or(0x8U)
                }
                accum.writeDirect(accumVal)
            }
            TCC -> {
                val flags  = getFlags()
                if (flags.carry != 0) {
                    accum.writeDirect(1U)
                } else {
                    accum.writeDirect(0U)
                }
                alu.setCarryVal(0U)
            }
            TCS -> {
                val flags  = getFlags()
                if (flags.carry != 0) {
                    accum.writeDirect(10U)
                } else {
                    accum.writeDirect(9U)
                }
                alu.setCarryVal(0U)
            }
            DAC -> {
                alu.setAluMode(AluSub)
                // DAC mode does not appear to use the previous borrow state like a normal subtract does.
                // So, clear it first
                alu.setCarryVal(0U)
                alu.evaluate(accum.readDirect(), 1U)
                accum.writeDirect(alu.value)
            }
            STC -> {
                alu.setCarryVal(1U)
            }
            DAA -> {
                val flags  = getFlags()
                var accumVal = accum.readDirect()
                if (accumVal > 9U || flags.carry != 0) {
                    accumVal += 6U
                    // This command does not reset the carry, only sets it
                    if (accumVal.and(alu.carryMask) != 0UL) {
                        alu.setCarryVal(1U)
                        accumVal = accumVal.and(alu.mask)
                    }
                    accum.writeDirect(accumVal)
                }
            }
            KBP -> {
                var accumVal = accum.readDirect()
                if (accumVal < 3U) {
                    // Do nothing
                } else if (accumVal == 4UL) {
                    accum.writeDirect(3U)
                } else if (accumVal == 8UL) {
                    accum.writeDirect(4U)
                } else {
                    accum.writeDirect(0xfU)
                }
            }
            DCL -> {
                // This command does not actually modify the accumulator
                // The pins can directly select 1 of 4 rams, or can be used through a 3/8 decoder
                // to select 1 of 8 rams.
                // The 4040 instruction manual actually describes how this works:
                /*
                (ACC)   |CM-RAMi Enabled            |Bank No.
                --------+---------------------------+--------
                X 0 0 0 |CM-RAM0                    |Bank 0
                X 0 0 1 |CM-RAM1                    |Bank 1
                X 0 1 0 |CM-RAM2                    |Bank 2
                X 1 0 0 |CM-RAM3                    |Bank 3
                X 0 1 1 |CM-RAM1,CM-RAM2            |Bank 4
                X 1 0 1 |CM-RAM1,CM-RAM3            |Bank 5
                X 1 1 0 |CM-RAM2,CM-RAM3            |Bank 6
                X 1 1 1 |CM-RAM1,CM-RAM2,CM-RAM3    |Bank 7
                */
                var accumVal = accum.readDirect()
                if (accumVal == 0UL) {
                    currentRamBank = 1U
                } else {
                    currentRamBank = accumVal.shl(1).toUInt()
                }
            }
        }
        updateFlags()
        val accumPost = accum.readDirectRaw()
        val carryPost = alu.carry
        val cmdString = accInstToString(inst.and(0xfU))

        if (log.isDebugEnabled)
            log.debug(String.format("Accumulator CMD %s: accum pre=%X, carryPre=%X, accum post=%X, carryPost=%X",
                cmdString, accumPre.toLong(), carryPre.toLong(), accumPost.toLong(), carryPost.toLong()))
    }
}

var instStrings = listOf("CLB", "CLC", "IAC", "CMC", "CMA", "RAL", "RAR", "TCC", "DAC", "TCS", "STC", "DAA", "KBP", "DCL")

fun accInstToString(inst: UInt): String {
    return instStrings[inst.toInt()]
}


class Alu(busWidth: Int, val dataBus: Bus, clk: Observable<Int>): Maskable() {
    val log = logger()
    val carryMask: ULong
    var mode = AluNone
    var carry = 0UL
    var changed = false
    var value = 0UL

    init {
        baseInit(busWidth, "ALU")
        carryMask = 1.toULong().shl(busWidth)
    }

    fun reset() {
        value = 0UL
        mode = AluNone
        carry = 0U
        changed = true
    }

    fun setCarryVal(value: ULong) {
        carry = value
        changed = true
    }

    fun complimentCarry() {
        if (carry == 0UL)
            carry = 1UL
        else
            carry = 0UL
        changed = true
    }

    fun setAluMode(mode: String) {
        this.mode = mode
        changed = true
        if (log.isDebugEnabled)
            log.debug("ALU mode set to " + mode)
    }

    fun evaluate(accIn: ULong, tmpIn: ULong) {
        var out = accIn
        val prevCarry = carry
        when (mode) {
            AluAdd -> {
                out = accIn + tmpIn
                if ((out.and(carryMask)) != 0UL) {
                    carry = 1U
                } else {
                    carry = 0U
                }
            }
            AluSub -> {
                // We set the carry bit to indicate NO borrow
                if (tmpIn > accIn) {
                    carry = 0U
                } else {
                    carry = 1U
                }
                out = accIn - tmpIn
                if (prevCarry != 0UL) {
                    out++
                }
            }
        }
        out = out.and(mask)
//        outputReg.writeDirect(out)
        dataBus.write(out)
        value = out
        if (log.isDebugEnabled) {
            log.debug(String.format(
                "** ALU: Evaluated mode %s, A=%X, T=%X, carryIn=%X, out=%X, carry=%X",
                mode, accIn, tmpIn, prevCarry, out, carry))
        }
    }

}