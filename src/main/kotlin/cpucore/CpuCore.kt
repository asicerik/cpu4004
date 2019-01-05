package cpucore

import common.Clocked
import io.reactivex.Observable
import utils.logger

class CpuCore(clk: Observable<Int>) {
    val log = logger()
    private val clkCount = Clocked<Int>(0, clk)
    private val counter = Clocked<Int>(0, clk)
    init {
        log.info("intialized")
        clk.subscribe {
            if (it==1) {
            } else {
                log.info("Raw={}, Clocked={}, counter={}", clkCount.raw, clkCount.clocked, counter.clocked)
                counter.raw = counter.clocked + 1
            }
        }
    }
    fun setRaw(value: Int) {
        clkCount.raw = value
    }

}