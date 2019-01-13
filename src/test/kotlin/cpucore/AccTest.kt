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
class AccTests {
    val core: CpuCore
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

    @Nested
    inner class AccInstructions {
        @Test
        fun ACC_CLB() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            val valA = 9L
            val valB = 9L
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA)
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valB))
            // Run the add
            runOneCycle(core, ADD.toLong().or(0x5))
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Now, clear both
            runOneCycle(core, CLB.toLong())

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
        @Test
        fun ACC_CLC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            val valA = 9L
            val valB = 9L
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA)
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valB))
            // Run the add
            runOneCycle(core, ADD.toLong().or(0x5))
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Now, clear carry only
            runOneCycle(core, CLC.toLong())

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
        }
        @Test
        fun ACC_IAC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            val valA = 9L
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valA))

            // Increment the accumulator
            runOneCycle(core, IAC.toLong())

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA+1)
        }
        @Test
        fun ACC_CMC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            runOneCycle(core, CMC.toLong())
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            runOneCycle(core, CMC.toLong())
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
        @Test
        fun ACC_CMA() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            val valA = 9L
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valA))

            // Compliment the accumulator
            runOneCycle(core, CMA.toLong())

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA.inv().and(0xf))
        }
        @Test
        fun ACC_RAL() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            var valA = 9L
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valA))

            // Rotate the accumulator
            runOneCycle(core, RAL.toLong())

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            valA = valA.shl(1).and(0xf)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA)

            // Rotate the accumulator
            runOneCycle(core, RAL.toLong())

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            // The carry bit should rotate into the lsb
            valA = valA.shl(1).and(0xf) + 1
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA)
        }
        @Test
        fun ACC_RAR() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)

            var valA = 9L
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valA))

            // Rotate the accumulator
            runOneCycle(core, RAR.toLong())

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            valA = valA.shr(1).and(0xf)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA)

            // Rotate the accumulator
            runOneCycle(core, RAR.toLong())

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            // The carry bit should rotate into the lsb
            valA = valA.shr(1).and(0xf).or(0x8)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA)
        }
        @Test
        fun ACC_TCC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            val valA = 9L
            val valB = 9L
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA)
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valB))
            // Run the add
            runOneCycle(core, ADD.toLong().or(0x5))
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Run the TCC, which should set the accumulator to 1 since the carry bit was set
            // it should also clear the carry bit
            runOneCycle(core, TCC.toLong())
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(1)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Reload the accumulator
            runOneCycle(core, LDM.toLong().or(valB))
            // Run the TCC, which should set the accumulator to 0 since the carry bit was NOT set
            runOneCycle(core, TCC.toLong())
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
        @Test
        fun ACC_DAC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            val valA = 9L
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valA))

            // Decrement the accumulator
            runOneCycle(core, DAC.toLong())

            // The carry bit should be set, indicating no borrow
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA-1)

            // Clear the accumulator
            runOneCycle(core, LDM.toLong().or(0))

            // Decrement the accumulator
            runOneCycle(core, DAC.toLong())

            // The carry bit should be clear, indicating borrow
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            //assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(15)
        }
        @Test
        fun ACC_TCS() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            val valA = 9L
            val valB = 9L
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA)
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM.toLong().or(valB))
            // Run the add
            runOneCycle(core, ADD.toLong().or(0x5))
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Run the TCS, which should set the accumulator to 10 since the carry bit was set
            // it should also clear the carry bit
            runOneCycle(core, TCS.toLong())
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(10)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Reload the accumulator
            runOneCycle(core, LDM.toLong().or(valB))
            // Run the TCC, which should set the accumulator to 9 since the carry bit was NOT set
            runOneCycle(core, TCS.toLong())
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(9)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
        }
        @Test
        fun ACC_STC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0L)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            // Now, set carry only
            runOneCycle(core, STC.toLong())

            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
    }
}