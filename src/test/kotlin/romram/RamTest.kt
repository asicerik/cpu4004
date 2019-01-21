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
import ram4002.Ram4002
import rom4001.Rom4001

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RamTests {
    val ram: Ram4002
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
        ram = Ram4002(dataBus, ioBus, clk, sync, cmRom)
        ram.createRamMemory(16,4, 4)
    }

    @Nested
    inner class IOTests {
        @Test
        fun Sync() {
            ram.reset()
            // It will take two cycles since sync is sent on clock 7
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)
            assertThat(ram.syncSeen).isEqualTo(true)
        }

        @Test
        fun SRC() {
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)

            // Make sure the ram responds to chip ID 0
            runOneSRCCycle(ram, 0, 0U, SRC)
            assertThat(ram.srcDetected).isTrue()

            // Make sure the ram does NOT respond to chip ID != 0
            runOneSRCCycle(ram, 0, 0x40U, SRC)
            assertThat(ram.srcDetected).isFalse()

            // Make sure the ram does not confuse a FIM with a SRC
            // since they share the same upper 4 bits
            runOneSRCCycle(ram, 0, 0U, FIM)
            assertThat(ram.srcDetected).isFalse()
        }
        @Test
        fun IoWrite() {
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)
            val ioData = 0xbUL

            // Run the SRC command first to arm the device
            runOneSRCCycle(ram, 0, 0U, SRC)
            assertThat(ram.srcDetected).isTrue()
            runOneIoWriteCycle(ram, 1, ioData, WMP)
            assertThat(ioBus.value).isEqualTo(ioData)
        }
        @Test
        fun IoRead() {
            // You should not be able to read a RAM's IO bus
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)
            val ioData = 0xbUL

            // Write the ioData to the ioBus
            ioBus.write(ioData)

            // Run the SRC command first to arm the device
            runOneSRCCycle(ram, 0, 0U, SRC)
//            assertThat(ram.srcDetected).isTrue()
            val res = romram.runOneIOReadCycle(ram, 0, RDR, 0)
            assertThat(res.second).isNotEqualTo(ioData.toByte())
        }
        @Test
        fun SrcDecodes() {
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)

            var chipId = 0UL
            var registerSel = 0UL
            var characterSel = 0UL

            // Verify the defaults work
            runOneSRCCycle(ram, 0, chipId.shl(2).or(registerSel).shl(4).or(characterSel), SRC)
            assertThat(ram.srcDetected).isTrue()
            assertThat(ram.srcDeviceID.toULong()).isEqualTo(chipId)
            assertThat(ram.srcRegisterSel.toULong()).isEqualTo(registerSel)
            assertThat(ram.srcCharacterSel.toULong()).isEqualTo(characterSel)

            // The chip should not respond to chipId 1
            chipId = 1UL
            runOneSRCCycle(ram, 0, chipId.shl(2).or(registerSel).shl(4).or(characterSel), SRC)
            assertThat(ram.srcDetected).isFalse()
            assertThat(ram.srcDeviceID.toULong()).isEqualTo(chipId)
            assertThat(ram.srcRegisterSel.toULong()).isEqualTo(registerSel)
            assertThat(ram.srcCharacterSel.toULong()).isEqualTo(characterSel)

            // Check the register select decode
            chipId = 0UL
            registerSel = 1UL
            runOneSRCCycle(ram, 0, chipId.shl(2).or(registerSel).shl(4).or(characterSel), SRC)
            assertThat(ram.srcDetected).isTrue()
            assertThat(ram.srcDeviceID.toULong()).isEqualTo(chipId)
            assertThat(ram.srcRegisterSel.toULong()).isEqualTo(registerSel)
            assertThat(ram.srcCharacterSel.toULong()).isEqualTo(characterSel)

            // And finally, the character select decode
            characterSel = 0x7UL
            runOneSRCCycle(ram, 0, chipId.shl(2).or(registerSel).shl(4).or(characterSel), SRC)
            assertThat(ram.srcDetected).isTrue()
            assertThat(ram.srcDeviceID.toULong()).isEqualTo(chipId)
            assertThat(ram.srcRegisterSel.toULong()).isEqualTo(registerSel)
            // We need one more clock to get the srcCharacterSel, but we don't want to mess up the sync timing, so run
            // a whole cycle
            runOneMemCycle(ram, 0, NOP, 0)
            assertThat(ram.srcCharacterSel.toULong()).isEqualTo(characterSel)
        }
        @Test
        fun CharacterWrite() {
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)

            var register = 0UL
            // Write each character into the RAM
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, i, WRM)
                assertThat(ram.srcCharacterSel.toULong()).isEqualTo(i)
            }
            // Go directly read the RAM data in register 0
            for (i in 0UL..15UL) {
                assertThat(ram.data[(i+register*4UL).toInt()].toULong()).isEqualTo(i)
            }
            // Now write a decrementing pattern to another register to make sure they don't clobber each other
            register = 1UL
            // Write each character into the RAM
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, 15UL-i, WRM)
                assertThat(ram.srcCharacterSel.toULong()).isEqualTo(i)
            }
            // Go directly read the RAM data in register 0
            for (i in 0UL..15UL) {
                assertThat(ram.data[(i).toInt()].toULong()).isEqualTo(i)
            }
            // Go directly read the RAM data in register 1
            for (i in 0UL..15UL) {
                assertThat(ram.data[(i+register*16UL).toInt()].toULong()).isEqualTo(15UL-i)
            }
        }
        @Test
        fun CharacterRead() {
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)
            var data = mutableListOf<ULong>()
            for (i in 0UL..15UL) {
                data.add(i)
            }

            var register = 0UL
            // Write each character into the RAM
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, i, WRM)
                assertThat(ram.srcCharacterSel.toULong()).isEqualTo(i)
            }
            // Read the data back using the RDM command
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res = runOneIOReadCycle(ram, 0, RDM, 0)
                assertThat(res.second.toULong()).isEqualTo(data[i.toInt()])
            }
            // Now write a decrementing pattern to another register to make sure they don't clobber each other
            register = 1UL
            // Write each character into the RAM
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, 15UL-i, WRM)
                assertThat(ram.srcCharacterSel.toULong()).isEqualTo(i)
            }
            // Read the data back from register 0 using the RDM command
            register = 0UL
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res = runOneIOReadCycle(ram, 0, RDM, 0)
                assertThat(res.second.toULong()).isEqualTo(data[i.toInt()])
            }
            // Read the data back from register 1 using the RDM command
            register = 1UL
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res = runOneIOReadCycle(ram, 0, RDM, 0)
                assertThat(res.second.toULong()).isEqualTo(data[(15UL-i).toInt()])
            }

        }
        @Test
        fun StatusWrite() {
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)

            var register = 0UL
            // Write each status character into the RAM
            for (i in 0UL..3UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, i, WR0.plus(i.toUInt()))
            }
            // Go directly read the RAM data in register 0
            for (i in 0UL..3UL) {
                assertThat(ram.statusMem[(i+register*4UL).toInt()].toULong()).isEqualTo(i)
            }
            // Now write a decrementing pattern to another register to make sure they don't clobber each other
            register = 1UL
            // Write each status character into the RAM
            for (i in 0UL..3UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, 15UL-i, WR0.plus(i.toUInt()))
            }
            // Go directly read the RAM data in register 0
            for (i in 0UL..3UL) {
                assertThat(ram.statusMem[(i).toInt()].toULong()).isEqualTo(i)
            }
            // Go directly read the RAM data in register 1
            for (i in 0UL..3UL) {
                assertThat(ram.statusMem[(i+register*4UL).toInt()].toULong()).isEqualTo(15UL-i)
            }
        }
        @Test
        fun StatusRead() {
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)

            var register = 0UL
            // Write each status character into the RAM
            for (i in 0UL..3UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, i, WR0.plus(i.toUInt()))
            }
            // Read back each status character from the RAM
            for (i in 0UL..3UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res = runOneIOReadCycle(ram, 0, RD0.plus(i.toUInt()), 0)
                assertThat(res.second.toULong()).isEqualTo(i)
            }
            // Now write a decrementing pattern to another register to make sure they don't clobber each other
            register = 1UL
            // Write each status character into the RAM
            for (i in 0UL..3UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, 15UL-i, WR0.plus(i.toUInt()))
            }
            // Read back each status character from the RAM register 0
            register = 0UL
            for (i in 0UL..3UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res = runOneIOReadCycle(ram, 0, RD0.plus(i.toUInt()), 0)
                assertThat(res.second.toULong()).isEqualTo(i)
            }
            // Read back each status character from the RAM register 1
            register = 1UL
            for (i in 0UL..3UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res =runOneIOReadCycle(ram, 0, RD0.plus(i.toUInt()), 0)
                assertThat(res.second.toULong()).isEqualTo(15UL-i)
            }
        }
        @Test
        fun SBM_ADM() {
            // From the RAM's perspective, SBM and ABM are equivalent to RDM
            ram.reset()
            // Sync the device
            runOneMemCycle(ram, 0, NOP, 0)
            runOneMemCycle(ram, 0, NOP, 0)
            var data = mutableListOf<ULong>()
            for (i in 0UL..15UL) {
                data.add(i)
            }

            var register = 0UL
            // Write each character into the RAM
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                runOneIoWriteCycle(ram, 0, i, WRM)
                assertThat(ram.srcCharacterSel.toULong()).isEqualTo(i)
            }
            // Read the data back using the SBM command
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res = runOneIOReadCycle(ram, 0, SBM, 0)
                assertThat(res.second.toULong()).isEqualTo(data[i.toInt()])
            }
            // Read the data back using the ADM command
            for (i in 0UL..15UL) {
                // Run the SRC command first to arm the device
                // The data is the character select
                runOneSRCCycle(ram, 0, register.shl(4).or(i), SRC)
                assertThat(ram.srcDetected).isTrue()
                val res = runOneIOReadCycle(ram, 0, ADM, 0)
                assertThat(res.second.toULong()).isEqualTo(data[i.toInt()])
            }
        }
    }
}