package common

import utils.logger

class Bus(val name: String) {
    val log = logger()

    var value = 0
    var writes = 0
        private set

    init {
        reset()
    }

    fun write(value: Int) {
        this.value = value
        if (log.isTraceEnabled)
            log.trace("{} written with {}. writes(pre)={}",
                name, Integer.toHexString(value), writes)
        writes++
    }

    fun reset() {
        writes = 0
    }
}