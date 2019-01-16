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
    val res = runOneIOReadCycle(dev, addr)
    return res.first
}

fun runOneIOReadCycle(dev: RomRamDecoder, addr: Long): Pair<Byte, Byte> {
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
            ioData = dev.extBus.value.and(0xf).toByte()
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

fun runOneSRCCycle(dev: RomRamDecoder, addr: Long, srcData: Long) {
    var log = LoggerFactory.getLogger("ROM Tests")
    for (i in 0..7) {
        // Sample the device outputs
        emitter!!.onNext(0)

        // Defaults
        dev.sync.raw = 1
        dev.cm.raw = 1

        if (i ==6) {
            dev.extBus.write(srcData.shr(4).and(0xf))
            dev.cm.raw = 0
        } else if (i == 7) {
            dev.extBus.write(srcData.and(0xf))
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
}

fun runOneIoWriteCycle(dev: RomRamDecoder, addr: Long, ioData: Long) {
    var log = LoggerFactory.getLogger("ROM Tests")
    for (i in 0..7) {
        // Sample the device outputs
        emitter!!.onNext(0)

        // Defaults
        dev.sync.raw = 1
        dev.cm.raw = 1

        if (i ==4) {
            dev.cm.raw = 0
        } else if (i ==6) {
            dev.extBus.write(ioData.and(0xf))
        } else if (i == 7) {
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
}


fun logIoState(dev: RomRamDecoder, clock: Int, clockCnt: Int, log: Logger) {
    if (log.isInfoEnabled)
        log.info(String.format("DBUS=%X, IOBUS=%X, SYNC=%d, CM=%d, CLK=%d, CCLK=%d, RCLK=%d",
            dev.extBus.value, dev.ioBus.value, dev.sync.clocked, dev.cm.clocked, clock, clockCnt, dev.clkCount.clocked))
}



