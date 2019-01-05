package common

import io.reactivex.Observable
import utils.logger

class Clocked<T>(val initialVal: T, clk: Observable<Int>) {
    val log = logger()
    var raw:T = initialVal
    var clocked:T = initialVal
        private set
    fun get(): T {
        return clocked
    }
    fun reset() {
        raw = initialVal
        clocked = initialVal
    }
    init {
        clk.subscribe {
            if (it==1) {
                clocked = raw
//                log.info("Base: Raw={}, Clocked={}", raw, clocked)
            }
        }
    }
}