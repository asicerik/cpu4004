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
class JumpFetchTests {
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
    inner class JumpTests {
        @Test
        fun JUN() {
            core.reset()
            Assertions.assertThat(core.sync.clocked).isEqualTo(1)
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            verifyJumpExtended(core, JMS.toLong(), true, true)
            var nextAddr = 0xabdL
            for (i in 0..3) {
                var addr = runOneCycle(core, NOP.toLong())
                assertThat(addr).isEqualTo(nextAddr)
                nextAddr++
            }
        }
        @Test
        fun JCN() {
            core.reset()
            Assertions.assertThat(core.sync.clocked).isEqualTo(1)
            var res = waitForSync(core)
            Assertions.assertThat(res.first).isEqualTo(true)
            // No flags set, should jump
            var conditionFlags = 0L
            var jumpExpected = true
            verifyJump(core, JCN.toLong().or(conditionFlags), jumpExpected)

            // Carry bit should not be set, no jump
            conditionFlags = 2L
            jumpExpected = false
            verifyJump(core, JCN.toLong().or(conditionFlags), jumpExpected)

            // Accumulator bit should be set, jump
            conditionFlags = 4L
            jumpExpected = true
            verifyJump(core, JCN.toLong().or(conditionFlags), jumpExpected)

            // Load the accumulator and verify no jump
            runOneCycle(core, LDM.toLong().or(5))
            conditionFlags = 4L
            jumpExpected = false
            verifyJump(core, JCN.toLong().or(conditionFlags), jumpExpected)

            // Run the inverse test
            runOneCycle(core, LDM.toLong().or(5))
            conditionFlags = 0xCL
            jumpExpected = true
            verifyJump(core, JCN.toLong().or(conditionFlags), jumpExpected)
        }

    }
}