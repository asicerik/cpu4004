package cpucore

fun step(count: Int) {
    for (i in 0 until count) {
        emitter!!.onNext(0)
        emitter!!.onNext(1)
    }
}

fun waitForSync(core: CpuCore): Pair<Boolean, Int> {
    var count = 0
    var syncSeen = false
    for (i in 0..15) {
        step(1)
        if (core.sync.clocked == 0) {
            if (!syncSeen) {
                syncSeen = true
                count = 0
            } else {
                // Run one extra clock to put us on cycle 0
                step(1)
                break
            }
        } else {
            count++
        }
    }
    return Pair(syncSeen, count)
}

fun runOneCycle(core: CpuCore, data: Long): Long {
    val res = runOneIOCycle(core, data)
    return res.first
}

fun runOneIOCycle(core: CpuCore, data: Long): Pair<Long, Long> {
    var addr = 0L
    var ioVal = 0L
    for (i in 0..7) {
        emitter!!.onNext(0)

        if (i < 3) {
            addr = addr.or(core.extDataBus.read().shl(i * 4))
        }
        if (i == 6) {
            ioVal = core.extDataBus.read()
        }
        if (i == 7) {
            ioVal = ioVal.or(core.extDataBus.read().shl(4))
        }
        emitter!!.onNext(1)
        if (i == 2) {
//                rlog.Debugf("runOneCycle: Writing upper data %X", (data>>4)&0xf)
            core.extDataBus.write(data.shr(4).and(0xf))
        } else if (i == 3) {
//                rlog.Debugf("runOneCycle: Writing lower data %X", data&0xf)
            core.extDataBus.write(data.and(0xf))
        }
    }
    return Pair(addr, ioVal)
}

