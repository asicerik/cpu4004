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
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // WRM - write RAM memory character
            val regPair = 6
            var ramBank = 0
            val ramAddr = 0x20
            val ramVal = 0xe
            // Load the accumulator with the ram bank we wish to use
            runOneCycle(core, LDM, ramBank)
            runOneCycle(core, DCL, 0)
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr.toULong(), regPair)
            // Load the accumulator
            runOneCycle(core, LDM, ramVal)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr.toULong())
            // Run the WRM command
            res2 = runOneIOCycle(core, WRM, 0)
            assertThat(res2.second.and(0xfU)).isEqualTo(ramVal.toULong())
        }

        @Test
        fun WMP() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // WMP - write RAM output port
            // Start with the default RAM bank (0)
            val regPair = 6
            var ramBank = 0
            val ramAddr = 0x20
            val ramVal = 0xe
            // Load the accumulator with the ram bank we wish to use
            runOneCycle(core, LDM, ramBank)
            runOneCycle(core, DCL, 0)
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr.toULong(), regPair)
            // Load the accumulator
            runOneCycle(core, LDM, ramVal)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr.toULong())
            // Run the WMP command
            res2 = runOneIOCycle(core, WMP, 0)
            assertThat(res2.second.and(0xfU)).isEqualTo(ramVal.toULong())

            // Run again selecting RAM bank 3
            // See the comments in the ALU for how the decode works
            ramBank = 4
            // Load the accumulator with the ram bank we wish to use
            runOneCycle(core, LDM, ramBank)
            runOneCycle(core, DCL, 0)
            // Load the accumulator with the output value
            runOneCycle(core, LDM, ramVal)
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr.toULong())
            // Run the WRR command
            res2 = runOneIOCycle(core, WMP, 0)
            assertThat(res2.second.and(0xfU)).isEqualTo(ramVal.toULong())
        }

        @Test
        fun WRR() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // WRR - write ROM i/o port
            val regPair = 6
            val romAddr = 0x20
            val romVal = 0xe
            // Load the ROM address into a register pair
            loadRegisterPair(core, romAddr.toULong(), regPair)
            // Load the accumulator
            runOneCycle(core, LDM, romVal)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(romAddr.toULong())
            // Run the WRR command
            res2 = runOneIOCycle(core, WRR, 0)
            assertThat(res2.second.and(0xfU)).isEqualTo(romVal.toULong())
        }
        @Test
        fun WRn() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // WR0-3 - write RAM memory status character
            val regPair = 6
            var ramBank = 0
            val ramAddr = 0x20
            val ramVal = 0xe
            // Load the accumulator with the ram bank we wish to use
            runOneCycle(core, LDM, ramBank)
            runOneCycle(core, DCL, 0)
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr.toULong(), regPair)
            // Load the accumulator
            runOneCycle(core, LDM, ramVal)
            for (i in 0..3) {
                // Run the SRC command
                var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
                assertThat(res2.second).isEqualTo(ramAddr.toULong())
                // Run the WRn command
                res2 = runOneIOCycle(core, WR0.plus(i.toUInt()), 0)
                assertThat(res2.second.and(0xfU)).isEqualTo(ramVal.toULong())
            }
        }
        @Test
        fun RDM() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // RDM - read RAM character
            val regPair = 6
            val ramAddr = 0x20UL
            val ramVal = 0xeUL
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the RDR command
            runOneIOReadCycle(core, RDM, 0, ramVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(ramVal)
        }
        @Test
        fun RDR() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // RDR - read ROM i/o port
            val regPair = 6
            val romAddr = 0x20UL
            val romVal = 0xeUL
            // Load the ROM address into a register pair
            loadRegisterPair(core, romAddr, regPair)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(romAddr)
            // Run the RDR command
            runOneIOReadCycle(core, RDR, 0, romVal)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(romVal)
        }
        @Test
        fun RDn() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // RD0-3 - rear RAM memory status character
            val regPair = 6
            val ramAddr = 0x20UL
            val ramVal = 0xeUL
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            for (i in 0..3) {
                // Run the SRC command
                var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
                assertThat(res2.second).isEqualTo(ramAddr)
                // Run the RDR command
                runOneIOReadCycle(core, RD0.plus(i.toUInt()), 0, ramVal)
                assertThat(core.aluCore.accum.readDirect()).isEqualTo(ramVal)
            }
        }
        @Test
        fun SBM() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // SBM - subtract RAM character value from accumulator
            val regPair = 6
            val ramAddr = 0x20UL
            val ramVal = 0x4
            var accumVal = 0x9
            var expVal = accumVal - ramVal
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            // Load the accumulator
            runOneCycle(core, LDM, accumVal)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM, 0, ramVal.toULong())
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
            // Carry should equal 1 meaning no borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Set accum to 4, so subtract will leave 1 because we have not cleared the carry bit
            accumVal = 4
            expVal = 1
            // Load the accumulator
            runOneCycle(core, LDM, accumVal)
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM, 0, ramVal.toULong())
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
            // Carry should equal 1 meaning no borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Now clear the carry bit and run it again
            runOneCycle(core, CLC, 0)
            expVal = 0
            // Load the accumulator
            runOneCycle(core, LDM, accumVal)
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM,0 , ramVal.toULong())
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
            // Carry should equal 1 meaning no borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            // Zero bit should be set
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            // Finally, run a test to cause a borrow
            accumVal = 1
            expVal = (accumVal - ramVal).and(0xf)
            // Clear the carry bit
            runOneCycle(core, CLC, 0)
            // Load the accumulator
            runOneCycle(core, LDM, accumVal)
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, SBM, 0, ramVal.toULong())
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
            // Carry should equal 0 meaning borrow occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

        }
        @Test
        fun ADM() {
            core.reset()
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            Assertions.assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            // ADM - add RAM character value to accumulator
            val regPair = 6
            val ramAddr = 0x20UL
            val ramVal = 0x4
            var accumVal = 0x2
            var expVal = accumVal + ramVal
            // Load the RAM address into a register pair
            loadRegisterPair(core, ramAddr, regPair)
            // Load the accumulator
            runOneCycle(core, LDM, accumVal)
            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, ADM, 0, ramVal.toULong())
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
            // Carry should equal 0
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Set accum to 12, so add will leave 0 and a carry
            accumVal = 12
            expVal = (accumVal + ramVal).and(0xf)
            // Load the accumulator
            runOneCycle(core, LDM, accumVal)
            // Run the SRC command
            res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(ramAddr)
            // Run the SBM command
            runOneIOReadCycle(core, ADM, 0, ramVal.toULong())
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
            // Carry should equal 1 meaning a carry occurred
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            // Zero should also be set
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
    }
}