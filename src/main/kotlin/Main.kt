import common.Bus
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Main")
    log.info("Welcome to the 4004 CPU Visualizer")

    var emitter: Emitter<Int>? =  null

    var clk: ConnectableObservable<Int> = Observable.create { it: Emitter<Int> ->
        emitter = it
    }.publish()
    clk.connect()
    val extDataBus = Bus("Ext Data Bus")
    val cpuCore = cpucore.CpuCore(extDataBus, clk)
    val rom0 = rom4001.Rom4001(extDataBus, clk, cpuCore.sync, cpuCore.cmRom)

    val loops = 32
    val startTime = System.currentTimeMillis()
    for (i in 0..loops) {
        LogState(cpuCore, rom0, log)
        extDataBus.reset()
        emitter!!.onNext(0)
        //Thread.sleep(100)
        emitter!!.onNext(1)
        //Thread.sleep(100)
    }
    val endTime = System.currentTimeMillis()
    val interval = (endTime - startTime)/1000.0

    println(String.format("Completed %d cycles in %3.2f seconds. Effective Clk speed %3.2f kHz",
        loops, interval, (loops/interval)/1000))
}

fun LogState(core: cpucore.CpuCore, rom: rom4001.Rom4001, log: Logger) {
    if (log.isInfoEnabled)
        log.info("PC={}, DBUS={}, SYNC={}, CCLK={}, RCLK={}",
            core.pc.clocked,
            Integer.toHexString(core.extDataBus.value),
            core.sync.clocked,
            core.getClkCount(),
            rom.getClkCount())
}