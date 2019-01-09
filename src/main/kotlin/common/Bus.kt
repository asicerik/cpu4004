package common

import utils.logger

class Bus: Maskable() {
    val log = logger()

    var value = 0L
    var writes = 0
        private set
    var bufferFollowers = mutableListOf<Pair<String, Buffer>>()

    fun init(width: Int, name: String) {
        super.baseInit(width, name)
    }

    fun addFollower(side: String, buffer: Buffer) {
        bufferFollowers.add(Pair(side, buffer))
    }

    fun write(value: Long) {
        this.value = value
        if (log.isTraceEnabled)
            log.trace("{} written with {}. writes(pre)={}",
                name, Integer.toHexString(value.toInt()), writes)
        for (buf in bufferFollowers) {
            buf.second.notifyBusWrite(buf.first, value)
        }
        writes++
    }

    fun read(): Long {
        return value
    }

    fun reset() {
        writes = 0
    }
}