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
    val accum = Register(0L, clk)
    val temp = Register(0L, clk)
    val flags = Register(0L, clk)
    val accumBus = Bus()
    val tempBus = Bus()
    val flagsBus = Bus()
    val mode = ""
    var currentRamBank = 1L
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
        currentRamBank = 1L
    }

    fun swap() {
        val tmp = temp.readDirect()
        temp.writeDirect(accum.readDirect())
        accum.writeDirect(tmp)
        log.trace(String.format("ALU Swap. accum=%X, temp=%X", accum.readDirect(), temp.readDirect()))
    }

    fun writeAccumulator() {
        accum.write()
        log.trace(String.format("ACCUM write with %X", dataBus.read()))
    }

    fun writeTemp() {
        temp.write()
        log.trace(String.format("Temp write with %X", dataBus.read()))
    }

    fun readAccumulator() {
        accum.read()
    }

    fun readTemp() {
        temp.read()
    }

    fun readTempDirect(): Long {
        return temp.readDirect()
    }

    fun readFlags() {
        flags.read()
        flagsDrivingBus = true
    }

    fun readFlagsDirect(): Long {
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
        if (flagsRaw.and(FlagPosZero) != 0L) {
            flagsVal.zero = 1
        }
        if (flagsRaw.and(FlagPosCarry) != 0L) {
            flagsVal.carry = 1
        }
        return flagsVal
    }

    fun updateFlags(): Long {
        val accum = accum.readDirect()
        var flagsVal = flags.readDirect()
        if (accum == 0L) {
            flagsVal = flagsVal.or(FlagPosZero)
        } else {
            flagsVal = flagsVal.and(FlagPosZero.inv())
        }
        if (alu.carry != 0L) {
            flagsVal = flagsVal.or(FlagPosCarry)
        } else {
            flagsVal = flagsVal.and(FlagPosCarry.inv())
        }
        flags.writeDirect(flagsVal)
        return flagsVal
    }

    fun exectuteAccInst(inst: Byte) {
        var accumPre = accum.readDirect()
        var carryPre = getFlags().carry
        // All this stuff is NOT cycle accurate. Who knows how this works
        // in the read CPU. Probably not this way though :)
        when (inst) {
            CLB -> {
                accum.writeDirect(0)
                alu.setCarryVal(0)
            }
            CLC -> {
                alu.setCarryVal(0)
            }
            IAC -> {
                alu.setAluMode(AluAdd)
                alu.evaluate(accum.readDirect(), 1)
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
                if (accumVal.and(alu.carryMask) != 0L) {
                    alu.setCarryVal(1)
                } else {
                    alu.setCarryVal(0)
                }
                // The low bit is the previous carry
                if (flags.carry != 0) {
                    accumVal = accumVal.or(1)
                }
                accum.writeDirect(accumVal)
            }
            RAR -> {
                val flags  = getFlags()
                var accumVal = accum.readDirect()
                val lsb = accumVal.and(0x1)
                accumVal = accumVal.shr(1)
                // Set the carry to the lsb before the shift
                alu.setCarryVal(lsb)
                // The high bit is the previous carry
                if (flags.carry != 0) {
                    accumVal = accumVal.or(0x8)
                }
                accum.writeDirect(accumVal)
            }
            TCC -> {
                val flags  = getFlags()
                if (flags.carry != 0) {
                    accum.writeDirect(1)
                } else {
                    accum.writeDirect(0)
                }
                alu.setCarryVal(0)
            }
            TCS -> {
                val flags  = getFlags()
                if (flags.carry != 0) {
                    accum.writeDirect(10)
                } else {
                    accum.writeDirect(9)
                }
                alu.setCarryVal(0)
            }
            DAC -> {
                alu.setAluMode(AluSub)
                // DAC mode does not appear to use the previous borrow state like a normal subtract does.
                // So, clear it first
                alu.setCarryVal(0)
                alu.evaluate(accum.readDirect(), 1)
                accum.writeDirect(alu.value)
            }
            STC -> {
                alu.setCarryVal(1)
            }
            DAA -> {
                val flags  = getFlags()
                var accumVal = accum.readDirect()
                if (accumVal > 9 || flags.carry != 0) {
                    accumVal += 6
                    // This command does not reset the carry, only sets it
                    if (accumVal.and(alu.carryMask) != 0L) {
                        alu.setCarryVal(1)
                        accumVal = accumVal.and(alu.mask)
                    }
                    accum.writeDirect(accumVal)
                }
            }
            KBP -> {
                var accumVal = accum.readDirect()
                if (accumVal < 3) {
                    // Do nothing
                } else if (accumVal == 4L) {
                    accum.writeDirect(3)
                } else if (accumVal == 8L) {
                    accum.writeDirect(4)
                } else {
                    accum.writeDirect(0xf)
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
                if (accumVal == 0L) {
                    currentRamBank = 1
                } else {
                    currentRamBank = accumVal.shl(1)
                }
            }
        }
        updateFlags()
        val accumPost = accum.readDirectRaw()
        val carryPost = alu.carry
        val cmdString = accInstToString(inst.and(0xf))

        if (log.isDebugEnabled)
            log.debug(String.format("Accumulator CMD %s: accum pre=%X, carryPre=%X, accum post=%X, carryPost=%X",
                cmdString, accumPre, carryPre, accumPost, carryPost))
    }
}

var instStrings = listOf("CLB", "CLC", "IAC", "CMC", "CMA", "RAL", "RAR", "TCC", "DAC", "TCS", "STC", "DAA", "KBP", "DCL")

fun accInstToString(inst: Byte): String {
    return instStrings[inst.toInt()]
}


class Alu(busWidth: Int, val dataBus: Bus, clk: Observable<Int>): Maskable() {
    val log = logger()
    val carryMask: Long
    var mode = AluNone
    var carry = 0L
    var changed = false
    var value = 0L

    init {
        baseInit(busWidth, "ALU")
        carryMask = 1.toLong().shl(busWidth)
    }

    fun reset() {
        value = 0L
        mode = AluNone
        carry = 0
        changed = true
    }

    fun setCarryVal(value: Long) {
        carry = value
        changed = true
    }

    fun complimentCarry() {
        if (carry == 0L)
            carry = 1L
        else
            carry = 0L
        changed = true
    }

    fun setAluMode(mode: String) {
        this.mode = mode
        changed = true
        if (log.isDebugEnabled)
            log.debug("ALU mode set to " + mode)
    }

    fun evaluate(accIn: Long, tmpIn: Long) {
        var out = accIn
        val prevCarry = carry
        when (mode) {
            AluAdd -> {
                out = accIn + tmpIn
                if ((out.and(carryMask)) != 0L) {
                    carry = 1
                } else {
                    carry = 0
                }
            }
            AluSub -> {
                // We set the carry bit to indicate NO borrow
                if (tmpIn > accIn) {
                    carry = 0
                } else {
                    carry = 1
                }
                out = accIn - tmpIn
                if (prevCarry != 0L) {
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