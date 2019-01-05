package cpucore

import common.Buffer
import common.Bus
import common.Clocked
import io.reactivex.Observable
import utils.logger
import java.time.Clock

class CpuCore(val extDataBus: Bus, clk: Observable<Int>) {
    val log = logger()
    // These are public so they can be shared and monitored
    // The contract is that you don't change them :)
    val sync = Clocked(1, clk)      // Sync signal between devices
    val cmRom = Clocked(0, clk)     // ROM select signal from CPU
    val cmRam = Clocked(0, clk)     // RAM select signals (4 bits) from CPU
    val pc = Clocked(0, clk)

    private val decoder = Decoder(clk)
    private val intDataBus = Bus("CPU Internal BUS")
    private val buffer = Buffer(intDataBus, extDataBus, "CPU Internal Buffer")
    private var syncSent = false

    init {
        clk.subscribe {
            // Process on the falling edge of the clock and prepare all data for the rising edge
            if (it==0) {
                process()
            }
        }
    }

    fun getClkCount():Int {
        return decoder.clkCount.clocked
    }

    private fun process() {
        resetFlags()
        decoder.clkAndSync()
        decoder.calculateFlags()
        update()
    }

    private fun resetFlags() {
        sync.raw = 1
        cmRom.raw = 1
        cmRam.raw = 0xf
        decoder.resetFlags()
        intDataBus.reset()
        // Transfer to the internal data bus if needed so the data is available to the decoder
        if (decoder.bufDir == BufDirIn) {
            buffer.bToA()
        }
    }

    fun update() {
        if (decoder.incPC != 0) {
            pc.raw = pc.clocked + 1
        }
        if (decoder.genSync != 0) {
            sync.raw = 0
        }
        if (decoder.genCmRom != 0) {
            cmRom.raw = 0
        }
        if (decoder.genCmRam > 0) {
            cmRam.raw = decoder.genCmRam.inv().and(0xf)
        }
        // Writes to the internal bus
        if (decoder.pcOut > 0) {
            intDataBus.write(pc.clocked.shr((decoder.pcOut-1)*4).and(0xf))
        }

        // Lastly, output to the external bus if needed
        if (decoder.bufDir == BufDirOut) {
            buffer.aToB()
        }
    }
}
