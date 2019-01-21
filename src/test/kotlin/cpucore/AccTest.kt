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
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            val valB = 9
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA.toULong())
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM, valB)
            // Run the add
            runOneCycle(core, ADD, 0x5)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Now, clear both
            runOneCycle(core, CLB, 0)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
        @Test
        fun ACC_CLC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            val valB = 9
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA.toULong())
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM, valB)
            // Run the add
            runOneCycle(core, ADD, 0x5)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Now, clear carry only
            runOneCycle(core, CLC, 0)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
        }
        @Test
        fun ACC_IAC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            // Load the accumulator
            runOneCycle(core, LDM, valA)

            // Increment the accumulator
            runOneCycle(core, IAC, 0)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(valA+1)
        }
        @Test
        fun ACC_CMC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            runOneCycle(core, CMC, 0)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            runOneCycle(core, CMC, 0)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
        @Test
        fun ACC_CMA() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            // Load the accumulator
            runOneCycle(core, LDM, valA)

            // Compliment the accumulator
            runOneCycle(core, CMA, 0)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(valA.inv().and(0xf).toULong())
        }
        @Test
        fun ACC_RAL() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            var valA = 9
            // Load the accumulator
            runOneCycle(core, LDM, valA)

            // Rotate the accumulator
            runOneCycle(core, RAL, 0)

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            valA = valA.shl(1).and(0xf)
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(valA)

            // Rotate the accumulator
            runOneCycle(core, RAL, 0)

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            // The carry bit should rotate into the lsb
            valA = valA.shl(1).and(0xf) + 1
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(valA)
        }
        @Test
        fun ACC_RAR() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            var valA = 9
            // Load the accumulator
            runOneCycle(core, LDM, valA)

            // Rotate the accumulator
            runOneCycle(core, RAR, 0)

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            valA = valA.shr(1).and(0xf)
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(valA)

            // Rotate the accumulator
            runOneCycle(core, RAR, 0)

            // msb should rotate into the carry bit
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            // The carry bit should rotate into the lsb
            valA = valA.shr(1).and(0xf).or(0x8)
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(valA)
        }
        @Test
        fun ACC_TCC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            val valB = 9
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA.toULong())
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM, valB)
            // Run the add
            runOneCycle(core, ADD, 0x5)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Run the TCC, which should set the accumulator to 1 since the carry bit was set
            // it should also clear the carry bit
            runOneCycle(core, TCC, 0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(1UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Reload the accumulator
            runOneCycle(core, LDM, valB)
            // Run the TCC, which should set the accumulator to 0 since the carry bit was NOT set
            runOneCycle(core, TCC, 0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
        @Test
        fun ACC_DAC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            // Load the accumulator
            runOneCycle(core, LDM, valA)

            // Decrement the accumulator
            runOneCycle(core, DAC, 0)

            // The carry bit should be set, indicating no borrow
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(valA-1)

            // Clear the accumulator
            runOneCycle(core, LDM, 0)

            // Decrement the accumulator
            runOneCycle(core, DAC, 0)

            // The carry bit should be clear, indicating borrow
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            //assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(15UL)
        }
        @Test
        fun ACC_TCS() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            val valB = 9
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA.toULong())
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM, valB)
            // Run the add
            runOneCycle(core, ADD, 0x5)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Run the TCS, which should set the accumulator to 10 since the carry bit was set
            // it should also clear the carry bit
            runOneCycle(core, TCS, 0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(10UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)

            // Reload the accumulator
            runOneCycle(core, LDM, valB)
            // Run the TCC, which should set the accumulator to 9 since the carry bit was NOT set
            runOneCycle(core, TCS, 0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(9UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
        }
        @Test
        fun ACC_STC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            // Now, set carry only
            runOneCycle(core, STC, 0)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
        }
        @Test
        fun ACC_DAA() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            // DAA - for accumulator values > 9 (or if carry is set), increment by 6
            // This operation can set the carry bit, but will not clear it
            // Load the accumulator with 9
            runOneCycle(core, LDM, 9)
            // Run the DAA cycle
            runOneCycle(core, DAA, 0)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(9UL)

            // Now set the carry
            runOneCycle(core, STC, 0)
            // Run the DAA cycle
            runOneCycle(core, DAA, 0)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(15UL)

            // Load the accumulator with 10
            runOneCycle(core, LDM,10)
            // Clear the carry bit
            runOneCycle(core, CLC, 0)
            // Run the DAA cycle
            runOneCycle(core, DAA, 0)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(1)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
        }
        @Test
        fun ACC_KBP() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            // KBP = Keyboard Process
            // If the accumulator is 0, it is left unchanged
            // Load the accumulator with 0
            runOneCycle(core, LDM, 0)
            runOneCycle(core, KBP, 0)

            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)

            for (i in 1..15) {
                // If the accumulator has 1 bit set, it will set the accumulator
                // with the bit position (1 based) that was set
                // Otherwise, it will set the accumulator to 15
                // Load the accumulator with i
                runOneCycle(core, LDM, i)
                runOneCycle(core, KBP, 0)

                assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
                assertThat(core.aluCore.getFlags().zero).isEqualTo(0)
                when (i) {
                    1 -> {
                        assertThat(core.aluCore.accum.readDirect()).isEqualTo(1UL)
                    }
                    2 -> {
                        assertThat(core.aluCore.accum.readDirect()).isEqualTo(2UL)
                    }
                    4 -> {
                        assertThat(core.aluCore.accum.readDirect()).isEqualTo(3UL)
                    }
                    8 -> {
                        assertThat(core.aluCore.accum.readDirect()).isEqualTo(4UL)
                    }
                    else -> {
                        assertThat(core.aluCore.accum.readDirect()).isEqualTo(15UL)
                    }
                }
            }
        }
        @Test
        fun ACC_DCL() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            assertThat(core.aluCore.getFlags().carry).isEqualTo(0)
            assertThat(core.aluCore.getFlags().zero).isEqualTo(1)

            // DCL - set the RAM selection register
            // First make sure it is 1
            assertThat(core.aluCore.currentRamBank).isEqualTo(1U)
            // Set the ram bank
            val ramBank = 7
            runOneCycle(core, LDM, ramBank)
            runOneCycle(core, DCL, 0)
            // Now make sure it has updated
            assertThat(core.aluCore.currentRamBank.toInt()).isEqualTo(ramBank.shl(1))
        }
    }
}
