package common

import io.reactivex.Observable
import utils.logger

class RomRamDecoder(val extBus: Bus, clk: Observable<Int>, val sync: Clocked<Int>, val cm: Clocked<Int>) {
    val log = logger()

    val clkCount = Clocked(0, clk)
    val addrReg = Register(0, clk)
    val valueRegs = mutableListOf<Register>()
    val intBus = Bus()
    val buffer = Buffer(intBus, extBus, "I/O Buf ")
    val data = mutableListOf<Byte>()

    // Renderer stuff
    var drivingBus = false

    var romMode = true              // Chip is a ROM by default
    private var id = 0L             // Which chip are we?

    // Flags
    var syncSeen = false
    var bufDir = BufDirNone // Which direction (if any) to transfer between internal and external bus
    var addrLoad = 0        // Load the address register. The value is the nybble (1 based)
    var romDataOut = 0      // Output the ROM data. The value is the nybble (1 based)
    var chipSelected = false

    init {
        intBus.init(4, "ROM Internal Bus")
        addrReg.init(intBus, 12, "Addr ")
        for (i in 0..2) {
            valueRegs.add(Register(0, clk))
            valueRegs[i].init(null, 8, "     ") // The spaces line up the values in the center
        }
    }

    fun setID(id: Long) {
        this.id = id
    }

    fun getID(): Long {
        return id
    }

    fun loadProgram(data: List<Byte>) {
        this.data.clear()
        this.data.addAll(0, data)
        calculateValueRegisters()
    }

    fun resetFlags() {
        addrLoad = 0
        romDataOut = 0

        // Transfer to the internal data bus if needed so the data is available to the decoder
        if (bufDir == BufDirIn) {
            buffer.bToA()
        }
        calculateValueRegisters()
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

        when (clkCount.clocked) {
            0 -> {
                addrLoad = 1
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            1 -> {
                addrLoad = 2
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            2 -> {
                // This is a little bit of a hack. We are looking at the external bus here.
                // This is because it is really hard to model the bus buffers in real-time.
                chipSelected = (extBus.value == id) && (cm.clocked == 0)
                if (log.isDebugEnabled)
                    log.debug("Chip ID {} ROM={}: selected = {}, ADDR={}", id, romMode, chipSelected, addrReg.readDirect())
                if (chipSelected && romMode) {
                    romDataOut = 1
                    bufDir = BufDirOut  // Transfer to the external bus
                }
            }
            3 -> {
                if (chipSelected && romMode) {
                    romDataOut = 2
                    bufDir = BufDirOut  // Transfer to the external bus
                }
            }
            7 -> {
                bufDir = BufDirIn   // Transfer to the internal bus
            }
        }
    }

    fun update() {
        // Address register and chip select
        if (addrLoad == 1) {
            addrReg.writeNybble(0)
        } else if (addrLoad == 2) {
            addrReg.writeNybble(1)
        }

        if (romDataOut == 1) {
            val outData = data[addrReg.readDirect().toInt()].toLong().shr(4).and(0xf)
            intBus.write(outData)
        } else if (romDataOut == 2) {
            val outData = data[addrReg.readDirect().toInt()].toLong().and((0xf))
            intBus.write(outData)
        }
    }

    fun clockOut() {
        if (bufDir == BufDirOut) {
            buffer.aToB()
            drivingBus = true
        }
    }

    fun calculateValueRegisters() {
        val curr = addrReg.readDirect().and(0xff).toInt()
        if (data.size < curr) {
            return
        }

        var first: Int
        valueRegs[0].selected = false
        valueRegs[1].selected = false
        valueRegs[2].selected = false

        when (curr) {
            0 -> {
                first = 0
                valueRegs[0].selected = chipSelected
            }
            (data.size - 1) -> {
                first = curr - 2
                valueRegs[2].selected = chipSelected
            }
            else -> {
                first = curr - 1
                valueRegs[1].selected = chipSelected
            }
        }

        valueRegs[0].writeDirect(data[first].toLong())
        valueRegs[1].writeDirect(data[first+1].toLong())
        valueRegs[2].writeDirect(data[first+2].toLong())
    }


}