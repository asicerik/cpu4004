package rom4001

import common.Bus
import common.Clocked
import common.RomRamDecoder
import cpucore.Decoder
import io.reactivex.Observable
import utils.logger

class Rom4001(extDataBus: Bus, clk: Observable<Int>, sync: Clocked<Int>, cm: Clocked<Int>) {
    val log = logger()
    // These are public so they can be shared and monitored
    // The contract is that you don't change them :)

    val decoder = RomRamDecoder(extDataBus, clk, sync, cm)

    private var romID = 0L   // Which ROM in the system

    init {
        clk.subscribe {
            // Process on the falling edge of the clock and prepare all data for the rising edge
            if (it==0) {
                process()
            } else {
                clockOut()
            }
        }
    }

    fun getClkCount():Int {
        return decoder.clkCount.clocked
    }

    fun setRomID(id: Long) {
        decoder.setID(id)
    }

    fun getRomID(): Long {
        return decoder.getID()
    }

    fun loadProgram(data: List<Byte>) {
        decoder.loadProgram(data)
    }

    private fun process() {
        resetFlags()
        decoder.clkAndSync()
        decoder.calculateFlags()
        update()
    }

    private fun resetFlags() {
        decoder.resetFlags()
    }

    fun update() {
        decoder.update()
    }

    fun clockOut() {
        decoder.clockOut()
    }
}