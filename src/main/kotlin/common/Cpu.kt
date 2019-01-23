package common

import com.sun.org.apache.xpath.internal.operations.Bool
import cpucore.CpuCore
import instruction.genLEDCountUsingAdd
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.slf4j.Logger
import ram4002.Ram4002
import rom4001.Rom4001
import utils.logger

const val maxRams   = 16        // Max number of RAM chips we can have in the system
const val maxRoms   = 16        // Max number of ROM chips we can have in the system

class Cpu {
    private var log = logger()
    private var extDataBus  = Bus()
    var ioBus       = Bus() // Input/output bus connects to ROM
    var outputBus   = Bus() // Output only buses can connect to ROM or RAM
    private var cpuCore: CpuCore?   = null
    private var rom: Rom4001?       = null
    private var ram: Ram4002?       = null
    private var emitter: Emitter<Int>? = null

    var cpuClockRate = 0.0
        private set

    fun init(): Boolean {
        // Main system clock
        val clk: ConnectableObservable<Int> = Observable.create { it: Emitter<Int> ->
            emitter = it
        }.publish()
        clk.connect()

        extDataBus.init(4, "Ext Data Bus")
        cpuCore = cpucore.CpuCore(extDataBus, clk)
        rom = Rom4001(extDataBus, ioBus, clk, cpuCore!!.sync, cpuCore!!.cmRom)
        ram = Ram4002(extDataBus, outputBus, clk, cpuCore!!.sync, cpuCore!!.cmRam)
        rom!!.omniMode = true
        ram!!.omniMode = true
        ram!!.createRamMemory(16, 4, 4)
        ioBus.init(64, String.format("ROM I/O Bus"))
        outputBus.init(64, String.format("RAM Output Bus"))
        return true
    }

    fun loadProgram(data: List<UByte>): Boolean {
        rom!!.loadProgram(data)
        return true
    }

    fun run() {
        var startTime = System.currentTimeMillis()
        var cpuClockCount = 0

        while (true) {
            // Bit 64 of iobus is the pause signal from the CPU
            if (ioBus.value.shr(63) != 0UL) {
                //log.debug("*********** Got pause instruction from CPU *********** ")
                val value:ULong = ioBus.value.shr(63)
                Thread.sleep(1)
            } else {
                //Thread.sleep(1)
                cpuClockCount++
                logState(cpuCore!!, rom!!, log)
                emitter!!.onNext(0)
                emitter!!.onNext(1)
                val endTime = System.currentTimeMillis()
                val interval = (endTime - startTime) / 1000.0
                if (interval >= 1) {
                    cpuClockRate = cpuClockCount / interval
                    cpuClockCount = 0
                    startTime = endTime
                }
            }
        }

    }
}

fun logState(core: cpucore.CpuCore, rom: rom4001.Rom4001, log: Logger) {
    if (log.isInfoEnabled)
        log.info("PC={}, DBUS={}, SYNC={}, CMROM={}, CMRAM={}, CCLK={}",
            core.addrStack.getProgramCounter(),
            Integer.toHexString(core.extDataBus.value.toInt()),
            core.sync.clocked,
            core.cmRom.clocked,
            core.cmRam.clocked,
            core.getClkCount())
}
