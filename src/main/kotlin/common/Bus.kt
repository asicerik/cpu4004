package common

import utils.logger

class Bus: Maskable() {
    val log = logger()

    var value = 0L
    var writes = 0
        private set

    fun init(width: Int, name: String) {
        super.baseInit(width, name)
    }

    fun write(value: Long) {
        this.value = value
        if (log.isTraceEnabled)
            log.trace("{} written with {}. writes(pre)={}",
                name, Integer.toHexString(value.toInt()), writes)
        writes++
    }

    fun read(): Long {
        return value
    }

    fun reset() {
        writes = 0
    }
}