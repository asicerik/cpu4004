package ram4002

import common.Bus
import common.Clocked
import common.RomRamDecoder
import cpucore.Decoder
import io.reactivex.Observable
import utils.logger

class Ram4002(extDataBus: Bus, ioBus: Bus?, clk: Observable<Int>, sync: Clocked<Int>, cm: Clocked<Int>):
    RomRamDecoder(extDataBus, ioBus, clk, sync, cm) {
    // These are public so they can be shared and monitored
    // The contract is that you don't change them :)

    private var romID = 0L   // Which RAM in the group of chips selected by a CMRAM line

    init {
        romMode = false
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
        return clkCount.clocked
    }

    fun setRamID(id: Long) {
        setID(id)
    }

    fun getRamID(): Long {
        return getID()
    }

    private fun process() {
        resetFlags()
        clkAndSync()
        calculateFlags()
        update()
    }

}