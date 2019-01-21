package cpucore

import common.Bus
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.experimental.or

var emitter: Emitter<Int>? = null

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpuCoreTest {
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
                val addr = runOneCycle(core, NOP, 0)
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
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            // Load the accumulator
            runOneCycle(core, LDM, 0x7)
            // Accumulator should now have 0x7
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(7UL)
        }
        @Test
        fun XCH() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            // Load the accumulator
            runOneCycle(core, LDM, 0x7)
            // Accumulator should now have 0x7
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(7UL)
            // Run the XCH cycle into register 2
            runOneCycle(core, XCH, 0x2)
            // Accumulator should now have 0x0
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            // Check index register 2
            assertThat(core.indexRegisters.readDirect(2)).isEqualTo(7UL)
        }
        @Test
        fun LD() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, 9U)
            // Load the accumulator
            runOneCycle(core, LD, 0x5)
            // Accumulator should now have 0x9
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(9UL)
        }
        @Test
        fun INC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, 9U)
            // Run the increment
            runOneCycle(core, INC, 0x5)
            // The register should now have 10
            assertThat(core.indexRegisters.readDirect(5)).isEqualTo(10UL)
        }
        @Test
        fun ADD() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 9
            val valB = 6
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA.toULong())
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM, valB)
            // Run the add
            runOneCycle(core, ADD, 0x5)

            // Accumulator should now have the sum
            val expVal = valA + valB
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
        }
        @Test
        fun SUB() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val valA = 6
            val valB = 9
            // Write directly to an index register
            core.indexRegisters.writeDirect(5, valA.toULong())
            // Write the other operand to the accumulator
            // Load the accumulator
            runOneCycle(core, LDM, valB)
            // Run the subtract
            runOneCycle(core, SUB, 0x5)

            // Accumulator should now have the difference
            val expVal = valB - valA
            assertThat(core.aluCore.accum.readDirect().toInt()).isEqualTo(expVal)
        }

        @Test
        fun SRC() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            var regPair = 2
            var expSrcVal =0xC
            // Populate the scratch registers with out expected value
            loadRegisterPair(core, 0xCU, regPair)

            // Use a LDM command to clear the lower 1/2 of the instruction register.
            // This is to make sure we don't decode a FIM command by mistake
            runOneCycle(core, LDM, 0x0)

            // Run the SRC command
            var res2 = runOneIOCycle(core, SRC, regPair.shl(1))
            assertThat(res2.second).isEqualTo(expSrcVal.toULong())
        }
        @Test
        fun FIM() {
            core.reset()
            val res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val regPair = 2
            val romVal = 0xdeU

            // The first cycle sets up the register pair to load into
            runOneCycle(core, FIM, regPair.shl(1))
            // The second cycle provides the data to load
            runOneCycle(core, romVal, 0)

            // Now check the registers
            assertThat(core.indexRegisters.readDirect(regPair.shl(1))).isEqualTo(romVal.shr(4).and(0xfU).toULong())
            assertThat(core.indexRegisters.readDirect(regPair.shl(1)+1)).isEqualTo(romVal.and(0xfU).toULong())
        }
        @Test
        fun FIN() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val regPair = 2
            val romAddr = 0xdeU
            val romData = 0x77U

            // Populate scratch registers pair 0 with out expected address
            loadRegisterPair(core, romAddr.toULong(), 0)

            // Run the command and verify the address on the next cycle
            var addr = runOneCycle(core, FIN, regPair.shl(1))

            // When the fetch is done, we should resume where we left off
            val expAddr = addr + 1U

            // Run the next cycle and provide the ROM read data
            addr = runOneCycle(core, romData, 0)
            assertThat(addr).isEqualTo(romAddr.toULong())

            // Run a final cycle to see where the program counter ended up
            addr = runOneCycle(core, romData, 0)
            assertThat(addr).isEqualTo(expAddr.toULong())
        }
        @Test
        fun JIN() {
            core.reset()
            var res = waitForSync(core)
            assertThat(res.first).isEqualTo(true)
            assertThat(core.aluCore.accum.readDirect()).isEqualTo(0UL)
            val regPair = 2
            val romAddr = 0xdeU
            val romData = 0x77U

            // Populate scratch registers pair 0 with out expected address
            loadRegisterPair(core, romAddr.toULong(), regPair)

            // Run the command and verify the address on the next cycle
            var addr = runOneCycle(core, JIN, regPair.shl(1))

            addr = runOneCycle(core, romData, 0)
            assertThat(addr).isEqualTo(romAddr.toULong())
        }
    }
}