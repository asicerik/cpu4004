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
        accum.init(null, BusWidth, "ACC ")
        temp.init(null, BusWidth, "TEMP ")
        flags.init(null, BusWidth, "FLAG ")
        accumBus.init(BusWidth, "")
        tempBus.init(BusWidth, "")
        flagsBus.init(BusWidth, "")
    }

    fun reset() {
        accum.reset()
        temp.reset()
        flags.reset()
    }
}