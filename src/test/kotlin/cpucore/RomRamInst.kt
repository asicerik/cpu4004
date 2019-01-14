package cpucore

import common.Bus
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RomRamInstTests {
    val core: CpuCore
    var dataBus = Bus()
    var clk: ConnectableObservable<Int>

    // Set everything up
    init {
        dataBus.init(4, "Test Bus")
        clk = Observable.create { it: Emitter<Int> ->
            emitter = it
        }.publish()
        clk.connect()
        core = CpuCore(dataBus, clk)
    }

    @Nested
    inner class Instructions {
        @Test
        fun WMP() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // WMP - write RAM output port
            // Start with the default RAM bank (0)
            val regPair = 6L
            var ramBank = 0L
            val ramAddr: Long = 0x20
            val ramVal: Long = 0xe
            // Load the accumulator with the ram bank we wish to use
            runOneCycle(core, LDM.toLong().or(ramBank))
            runOneCycle(core, DCL.toLong())
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(ramVal))
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the WRR command
            res2 = runOneIOCycle(core, WMP.toLong())
            assertThat(res2.second.and(0xf)).isEqualTo(ramVal)

            // Run again selecting RAM bank 3
            // NOTE: We want CMRAM[3] to be asserted. The DCL value has 1 added to it, so 7+1 = 8
            ramBank = 7
            // Load the accumulator with the ram bank we wish to use
            runOneCycle(core, LDM.toLong().or(ramBank))
            runOneCycle(core, DCL.toLong())
            // Load the accumulator with the output value
            runOneCycle(core, LDM.toLong().or(ramVal))
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the WRR command
            res2 = runOneIOCycle(core, WMP.toLong())
            assertThat(res2.second.and(0xf)).isEqualTo(ramVal)
        }

        @Test
        fun WRR() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // WRR - write ROM i/o port
            val regPair = 6L
            val romAddr: Long = 0x20
            val romVal: Long = 0xe
            // Load the ROM address into a register pair
            loadRegisterPair(core, romAddr, regPair)
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(romVal))
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(romAddr)
            // Run the WRR command
            res2 = runOneIOCycle(core, WRR.toLong())
            assertThat(res2.second.and(0xf)).isEqualTo(romVal)
        }
    }
}