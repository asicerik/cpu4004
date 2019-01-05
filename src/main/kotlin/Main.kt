import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Main")
    log.info("Welcome to the 4004 CPU Visualizer")

    var emitter: Emitter<Int>? =  null

    var clk: ConnectableObservable<Int> = Observable.create { it: Emitter<Int> ->
        emitter = it
    }.publish()
    clk.connect()
    val cpuCore = cpucore.CpuCore(clk)

    val loops = 8
    val startTime = System.currentTimeMillis()
    for (i in 0..loops) {
        cpuCore.setRaw(i)
        emitter!!.onNext(0)
        //Thread.sleep(100)
        cpuCore.setRaw(i)
        emitter!!.onNext(1)
        //Thread.sleep(100)
    }
    val endTime = System.currentTimeMillis()
    val interval = (endTime - startTime)/1000.0

    println(String.format("Completed %d cycles in %3.2f seconds. Effective Clk speed %3.2f kHz",
        loops, interval, (loops/interval)/1000))
}