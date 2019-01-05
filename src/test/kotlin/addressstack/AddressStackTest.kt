package addressstack

import common.Bus
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AddressStackTest {
    val addrStack: AddressStack
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
        addrStack = AddressStack(dataBus, clk)
    }

    @Nested
    inner class ProgramCounterTest {
        @Test
        fun increment() {
            addrStack.reset()
            var pc = addrStack.getProgramCounter()
            assertThat(pc).isEqualTo(0)

            addrStack.incrementProgramCounter()
            // We have not clocked it yet, so it should still be 0
            assertThat(pc).isEqualTo(0)

            step(1)
            pc = addrStack.getProgramCounter()
            assertThat(pc).isEqualTo(1)
        }
        @Test
        fun readAndWrite() {
            addrStack.reset()
            dataBus.write(0xa)
            addrStack.writeProgramCounter(0)
            dataBus.write(0xb)
            addrStack.writeProgramCounter(1)

            var pc = addrStack.getProgramCounter()
            // We have not clocked it yet, so it should still be 0
            assertThat(pc).isEqualTo(0)

            step(1)
            pc = addrStack.getProgramCounter()
            assertThat(pc).isEqualTo(0xba)

            // Now do the reads to make sure the bus gets written
            dataBus.write(0)
            addrStack.readProgramCounter(0)
            assertThat(dataBus.value).isEqualTo(0xa)
            addrStack.readProgramCounter(1)
            assertThat(dataBus.value).isEqualTo(0xb)
        }
    }

    fun step(count: Int) {
        for (i in 0 until count) {
            emitter!!.onNext(0)
            emitter!!.onNext(1)
        }
    }
}