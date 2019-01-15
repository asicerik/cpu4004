package romram

import common.Bus
import common.RomRamDecoder
import cpucore.*
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.experimental.or

fun step(count: Int) {
    for (i in 0 until count) {
        emitter!!.onNext(0)
        emitter!!.onNext(1)
    }
}

fun runOneCycle(dev: RomRamDecoder, addr: Long): Byte {
    val res = runOneIOCycle(dev, addr)
    return res.first
}

fun runOneIOCycle(dev: RomRamDecoder, addr: Long): Pair<Byte, Byte> {
    var inst:Byte = 0
    var ioData:Byte = 0
    var log = LoggerFactory.getLogger("ROM Tests")
    for (i in 0..7) {
        //val i = (j-1).and(0x7)
        // Read the instruction/ROM data
        if (i == 4) {
            inst = dev.extBus.read().shl(4).toByte()
        }
        if (i == 5) {
            inst = inst.or(dev.extBus.read().toByte())
        }
        // Sample the device outputs
        emitter!!.onNext(0)

        // Defaults
        dev.sync.raw = 1
        dev.cm.raw = 1

        if (i == 7) {
            dev.sync.raw = 0
        }
        // Write out the address one nybble at a time
        if (i in 0..2) {
            dev.extBus.write(addr.shr((i)*4).and(0xf))
            if (i == 2) {
                dev.cm.raw = 0
            }
        }
        emitter!!.onNext(1)
        // Setup the device inputs
        logIoState(dev, 1, i, log)

    }
    return Pair(inst, ioData)
}

fun logIoState(dev: RomRamDecoder, clock: Int, clockCnt: Int, log: Logger) {
    if (log.isInfoEnabled)
        log.info(String.format("DBUS=%X, SYNC=%d, CM=%d, CLK=%d, CCLK=%d, RCLK=%d",
            dev.extBus.value, dev.sync.clocked, dev.cm.clocked, clock, clockCnt, dev.clkCount.clocked))
}



