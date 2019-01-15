package romram

import common.Bus
import common.Clocked
import cpucore.*
import instruction.addInstruction
import instruction.fillEmptyProgramData
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.observables.ConnectableObservable
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import rom4001.Rom4001

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RomTests {
    val rom: Rom4001
    var dataBus = Bus()
    var ioBus = Bus()
    var clk: ConnectableObservable<Int>
    var sync: Clocked<Int>          // Sync signal between devices
    var cmRom: Clocked<Int>         // ROM select signal from CPU
    var cmRam: Clocked<Int>         // RAM select signals (4 bits) from CPU

    // Set everything up
    init {
        dataBus.init(4, "Test Bus")
        ioBus.init(4, "Test IO Bus")
        clk = Observable.create { it: Emitter<Int> ->
            emitter = it
        }.publish()
        clk.connect()
        sync = Clocked(1, clk)        // Sync signal between devices
        cmRom = Clocked(1, clk)       // ROM select signal from CPU
        cmRam = Clocked(0xf, clk)     // RAM select signals (4 bits) from CPU
        rom = Rom4001(dataBus, ioBus, clk, sync, cmRom)
    }

    @Nested
    inner class IOTests {
        @Test
        fun Sync() {
            rom.reset()
            var data = mutableListOf<Byte>()
            fillEmptyProgramData(data)
            rom.loadProgram(data)
            // It will take two cycles since sync is sent on clock 7
            runOneCycle(rom, 0)
            runOneCycle(rom, 0)
            assertThat(rom.syncSeen).isEqualTo(true)
        }
        @Test
        fun InstRead() {
            rom.reset()
            var data = mutableListOf<Byte>()
            // Sync the device
            runOneCycle(rom, 0)
            runOneCycle(rom, 0)
            // Generate a simple program
            addInstruction(data, 0x01)
            addInstruction(data, 0x23)
            addInstruction(data, 0x45)
            addInstruction(data, 0x67)
            fillEmptyProgramData(data)
            rom.loadProgram(data)
            for (i in 0..3) {
                val res = runOneCycle(rom, i.toLong())
                assertThat(res).isEqualTo(data[i])
            }
        }
        @Test
        fun ChipID() {
            rom.reset()
            var data = mutableListOf<Byte>()
            // Sync the device
            runOneCycle(rom, 0)
            runOneCycle(rom, 0)
            // Generate a simple program
            addInstruction(data, 0xDE.toByte())
            fillEmptyProgramData(data)
            rom.loadProgram(data)
            // Make sure the rom responds to chip 0 addresses (the default)
            var res = runOneCycle(rom, 0)
            assertThat(res).isEqualTo(data[0])
            assertThat(rom.chipSelected).isTrue()

            // Now change the chip ID
            rom.setRomID(3)
            // Make sure the rom does NOT respond to chip 0 addresses
            res = runOneCycle(rom, 0)
            assertThat(res).isNotEqualTo(data[0])
            assertThat(rom.chipSelected).isFalse()

            // Finally, check the ROM with the right address
            // Make sure the rom responds to chip 3 addresses
            res = runOneCycle(rom, 0x300)
            assertThat(res).isEqualTo(data[0])
            assertThat(rom.chipSelected).isTrue()
        }
    }
}