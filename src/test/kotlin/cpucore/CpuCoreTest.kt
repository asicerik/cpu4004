package cpucore

import common.Bus
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpuCoreTest {
    val core: CpuCore
    var emitter: Emitter<Int>? = null
    var dataBus = Bus()
    var clk: ConnectableObservable<Int>

    // Set everything up
    init {
        dataBus.init(4,"Test Bus")
        clk = Observable.create { it: Emitter<Int> ->
            emitter = it
        }.publish()
        clk.connect()
        core = CpuCore(dataBus, clk)
    }

    fun step(count: Int) {
        for (i in 0 until count) {
            emitter!!.onNext(0)
            emitter!!.onNext(1)
        }
    }

    @Nested
    inner class BasicTests {
        @Test
        fun sync() {
            core.reset()
            assertThat(core.sync.clocked).isEqualTo(1)
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(res.second).isEqualTo(7)
        }
        @Test
        fun programCounter() {
            core.reset()
            val res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            for (i in 2..6) {
                val addr = runOneCycle(core, 0)
                assertThat(addr.toInt()).isEqualTo(i)
            }
        }
    }

    @Nested
    inner class Instructions {
        @Test
        fun LDM() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(0x7))
            // Accumulator should now have 0x7
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(7L)
        }
        @Test
        fun XCH() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(0x7))
            // Accumulator should now have 0x7
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(7L)
            // Run the XCH cycle into register 2
            runOneCycle(core, XCH.toLong().or(0x2))
            // Accumulator should now have 0x0
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            // Check index register 2
            assertThat(core.indexRegisters.readDirect(2)).isEqualTo(7L)
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

}