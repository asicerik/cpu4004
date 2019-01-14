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
        fun WRM() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // WRM - write RAM memory character
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
            // Run the WRM command
            res2 = runOneIOCycle(core, WRM.toLong())
            assertThat(res2.second.and(0xf)).isEqualTo(ramVal)
        }

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
            // Run the WMP command
            res2 = runOneIOCycle(core, WMP.toLong())
            assertThat(res2.second.and(0xf)).isEqualTo(ramVal)

            // Run again selecting RAM bank 3
            // See the comments in the ALU for how the decode works
            ramBank = 4
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
        @Test
        fun WRn() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // WR0-3 - write RAM memory status character
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
            for (i in 0..3) {
                // Run the SRC command
                var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
                assertThat(res2.second).isEqualTo(ramAddr)
                // Run the WRn command
                res2 = runOneIOCycle(core, WR0.toLong().plus(i))
                assertThat(res2.second.and(0xf)).isEqualTo(ramVal)
            }
        }
        @Test
        fun RDM() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // RDM - read RAM character
            val regPair = 6L
            val ramAddr: Long = 0x20
            val ramVal: Long = 0xe
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the RDR command
            runOneIOReadCycle(core, RDM.toLong(), ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(ramVal)
        }
        @Test
        fun RDR() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // RDR - read ROM i/o port
            val regPair = 6L
            val romAddr: Long = 0x20
            val romVal: Long = 0xe
            // Load the ROM address into a register pair
            loadRegisterPair(core, romAddr, regPair)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(romAddr)
            // Run the RDR command
            runOneIOReadCycle(core, RDR.toLong(), romVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(romVal)
        }
        @Test
        fun RDn() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // RD0-3 - rear RAM memory status character
            val regPair = 6L
            val ramAddr: Long = 0x20
            val ramVal: Long = 0xe
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            for (i in 0..3) {
                // Run the SRC command
                var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
                assertThat(res2.second).isEqualTo(ramAddr)
                // Run the RDR command
                runOneIOReadCycle(core, RD0.toLong()+i, ramVal)
                assertThat(core.aluCore.accum.readDirect()).isEqualTo(ramVal)
            }
        }
        @Test
        fun SBM() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // SBM - subtract RAM character value from accumulator
            val regPair = 6L
            val ramAddr: Long = 0x20
            val ramVal: Long = 0x4
            var accumVal: Long = 0x9
            var expVal = accumVal - ramVal
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(accumVal))
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM.toLong(), ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(expVal)
            // Carry should equal 1 meaning no borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Set accum to 4, so subtract will leave 1 because we have not cleared the carry bit
            accumVal = 4
            expVal = 1
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(accumVal))
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM.toLong(), ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(expVal)
            // Carry should equal 1 meaning no borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Now clear the carry bit and run it again
            runOneCycle(core, CLC.toLong())
            expVal = 0
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(accumVal))
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM.toLong(), ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(expVal)
            // Carry should equal 1 meaning no borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            // Zero bit should be set
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            // Finally, run a test to cause a borrow
            accumVal = 1
            expVal = (accumVal - ramVal).and(0xf)
            // Clear the carry bit
            runOneCycle(core, CLC.toLong())
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(accumVal))
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM.toLong(), ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(expVal)
            // Carry should equal 0 meaning borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

        }
        @Test
        fun ADM() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            // ADM - add RAM character value to accumulator
            val regPair = 6L
            val ramAddr: Long = 0x20
            val ramVal: Long = 0x4
            var accumVal: Long = 0x2
            var expVal = accumVal + ramVal
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(accumVal))
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, ADM.toLong(), ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(expVal)
            // Carry should equal 0
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Set accum to 12, so add will leave 0 and a carry
            accumVal = 12
            expVal = (accumVal + ramVal).and(0xf)
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(accumVal))
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC.toLong().or(regPair.shl(1)))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, ADM.toLong(), ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(expVal)
            // Carry should equal 1 meaning a carry occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            // Zero should also be set
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
    }
}