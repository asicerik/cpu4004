package common

import cpucore.SRC
import cpucore.WRR
import io.reactivex.Observable
import utils.logger

class RomRamDecoder(val extBus: Bus, val ioBus: Bus, clk: Observable<Int>, val sync: Clocked<Int>, val cm: Clocked<Int>) {
    val log = logger()

    val clkCount = Clocked(0, clk)
    val addrReg = Register(0, clk)
    val instReg = Register(0, clk)
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
    var bufDir = BufDirNone     // Which direction (if any) to transfer between internal and external bus
    var addrLoad = 0            // Load the address register. The value is the nybble (1 based)
    var romDataOut = 0          // Output the ROM data. The value is the nybble (1 based)
    var chipSelected = false
    var srcDetected  = false    // SRC command was detected
    var srcRomID     = 0L       // The ROM ID sent in the SRC command
    var ioOpDetected = false    // IO Operation was detected

    init {
        intBus.init(4, "ROM Internal Bus")
        addrReg.init(intBus, 12, "Addr ")
        instReg.init(intBus, 8, "INST ")
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
                } else {
                    bufDir = BufDirIn   // Transfer to the internal bus
                }

            }
            3 -> {
                if (chipSelected && romMode) {
                    romDataOut = 2
                    bufDir = BufDirOut  // Transfer to the external bus
                }
                // Check for IO ops before we update the SRC flag
                if (extBus.value == WRR.toLong().shr(4).and(0xf)) {
                    if (srcDetected) {
                        log.debug("ROM: IO instruction detected")
                        ioOpDetected = true
                    } else {
                        ioOpDetected = false
                    }
                } else {
                    ioOpDetected = false
                }

                // NOTE: FIM and SRC have the same upper 4 bits
                // We won't know which instruction it is until the next cycle
                if (extBus.value == SRC.toLong().shr(4).and(0xf)) {
                    log.debug("ROM: FIM/SRC instruction detected")
                    srcDetected = true
                } else {
                    srcDetected = false
                }

                instReg.writeNybbleDirect(1, extBus.value)
            }
            4 -> {
                bufDir = BufDirIn   // Transfer to the internal bus
                instReg.writeNybbleDirect(0, extBus.value)
            }
            5 -> {
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            6 -> {
                if (srcDetected) {
                    bufDir = BufDirIn   // Transfer to the internal bus
                    srcRomID = intBus.read().and(0xf)
                    if (srcRomID != id) {
                        log.debug(String.format("ROM: SRC command was NOT for us. Our chipID=%02X, cmd chipID=%02X",
                            id, srcRomID))
                        srcDetected = false
                    } else {
                        log.debug(String.format("ROM: SRC command WAS for us. Our chipID=%02X", id))
                    }
                }
                if (ioOpDetected) {
                    // Copy the data to the internal bus
                    bufDir = BufDirIn   // Transfer to the internal bus
                    val cmd = instReg.readDirect()
                    when (cmd) {
                        WRR.toLong().and(0xff) -> {
                            // IO Write
                            ioBus.reset()
                            ioBus.write(intBus.read())
                        }
                    }
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