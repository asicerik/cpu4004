package alu

import common.Bus
import common.BusWidth
import common.Maskable
import common.Register
import cpucore.*
import io.reactivex.Observable
import utils.logger

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
    var currentRamBank = 0L
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
        currentRamBank = 0L
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
}

class Alu(busWidth: Int, val dataBus: Bus, clk: Observable<Int>): Maskable() {
    val log = logger()
    val carryMask: Long
    var mode = ""
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
    }

//    fun readOutput() {
//        outputReg.read()
//    }
//
//    fun readOutputDirect(): Long {
//        return outputReg.readDirect()
//    }
//
    fun setCarry() {
        carry = 1L
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