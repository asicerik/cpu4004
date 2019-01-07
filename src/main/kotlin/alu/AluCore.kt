package alu

import addressstack.stackDepth
import common.Bus
import common.BusWidth
import common.Register
import io.reactivex.Observable
import utils.logger

class AluCore(val dataBus: Bus, clk: Observable<Int>) {
    val log = logger()

    val accum = Register(0L, clk)
    val temp = Register(0L, clk)
    val flags = Register(0L, clk)
    val accumBus = Bus()
    val tempBus = Bus()
    val flagsBus = Bus()
    val mode = ""
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
    }

    fun reset() {
        accum.reset()
        temp.reset()
        flags.reset()
    }

    fun swap() {
        val tmp = temp.readDirect()
        temp.writeDirect(accum.readDirect())
        accum.writeDirect(tmp)
        log.trace(String.format("ALU Swap. accum=%X, temp=%X", accum.readDirect(), temp.readDirect()))
    }

    fun writeAccumulator() {
        accum.write()
        log.trace(String.format("ALU write with %X", dataBus.read()))
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
}