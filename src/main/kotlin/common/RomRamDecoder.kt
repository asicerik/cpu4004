package common

import io.reactivex.Observable
import utils.logger

class RomRamDecoder(val extBus: Bus, clk: Observable<Int>, val sync: Clocked<Int>, val cm: Clocked<Int>) {
    val log = logger()

    val clkCount = Clocked(0, clk)
    val addrReg = Clocked(0, clk)
    var syncSeen = false

    var romMode = true              // Chip is a ROM by default
    private var addrLoad = 0        // Load the address register. The value is the nybble (1 based)
    private var romDataOut = 0      // Output the ROM data. The value is the nybble (1 based)

    private var id = 0
    private var chipSelected = false

    fun setID(id: Int) {
        this.id = id
    }

    fun resetFlags() {
        addrLoad = 0
        romDataOut = 0
    }

    fun clkAndSync() {
        if (sync.clocked == 0 || clkCount.clocked == 7) {
            clkCount.raw = 0
            syncSeen = true
        } else if (syncSeen) {
            clkCount.raw = clkCount.clocked + 1
        }
    }

    fun calculateFlags() {
        if (!syncSeen) {
            return
        }

        // NOTE: we are using the raw count here. This allows us to use the next clock cycle count
        // as our index. That way everything is not off by one
        when (clkCount.raw) {
            0 -> {
                addrLoad = 1
            }
            1 -> {
                addrLoad = 2
            }
            2 -> {
                addrLoad = 3
            }
            3 -> {
                if (chipSelected && romMode) {
                    romDataOut = 1
                }
            }
            4 -> {
                if (chipSelected && romMode) {
                    romDataOut = 2
                }
            }
        }
    }

    fun update() {
        // Address register and chip select
        if (addrLoad == 1) {
            addrReg.raw = extBus.value
        } else if (addrLoad == 2) {
            addrReg.raw.or(extBus.value.shl(4))
        } else if (addrLoad == 3) {
            chipSelected = (extBus.value == id) && (cm.clocked != 0)
            if (log.isDebugEnabled)
                log.debug("Chip ID {} ROM={}: selected = {}, ADDR={}", id, romMode, chipSelected, addrReg.clocked)
        }

        if (romDataOut == 1) {
            extBus.write(0x8)
        } else if (romDataOut == 2)
            extBus.write(0x9)
    }

}