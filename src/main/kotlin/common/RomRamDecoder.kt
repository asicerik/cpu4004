package common

import cpucore.*
import io.reactivex.Observable
import utils.logger
import kotlin.experimental.and

open class RomRamDecoder(val extBus: Bus, val ioBus: Bus, clk: Observable<Int>, val sync: Clocked<Int>, val cm: Clocked<Int>) {
    val log = logger()

    val clkCount = Clocked(0, clk)
    val addrReg = Register(0, clk)
    val instReg = Register(0, clk)
    val valueRegs = mutableListOf<Register>()
    val intBus = Bus()
    val buffer = Buffer(intBus, extBus, "I/O Buf ")
    var data = mutableListOf<Byte>()
    var statusMem = mutableListOf<Byte>()   // For RAM only
    var registers = 0L                      // How many registers (banks) of memory in RAM
    var characters = 0L                     // How many characters per bank (register)
    var statusCharacters = 0L               // How many status characters per bank (register)

    // Renderer stuff
    var drivingBus = false

    var romMode = true              // Chip is a ROM by default
    private var id = 0L             // Which chip are we?

    // Flags
    var syncSeen = false
    var bufDir = BufDirNone     // Which direction (if any) to transfer between internal and external bus
    var addrLoad = 0            // Load the address register. The value is the nybble (1 based)
    var romDataOut = 0          // Output the ROM data. The value is the nybble (1 based)
    var ioRead = false          // If true, output our IO bus data to the data bus
    var memRead = false         // If true, output the selected RAM character
    var statusRead = -1L        // If >=0, output the selected RAM status character
    var chipSelected = false
    var srcDetected  = false    // SRC command was detected
    var srcDeviceID     = 0L    // The ROM/RAM ID sent in the SRC command
    var srcRegisterSel  = 0L    // The RAM register select sent in the SRC command
    var srcCharacterSel = 0L    // The RAM character select sent in the SRC command
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

    fun reset() {
        syncSeen    = false
        bufDir      = BufDirNone
        addrLoad    = 0
        romDataOut  = 0
        ioRead      = false
        memRead     = false
        statusRead  = -1L
        chipSelected= false
        srcDetected = false
        srcDeviceID    = 0L
        ioOpDetected= false
        drivingBus  = false
        for (i in 0 until data.size) {
            data[i] = 0
        }
    }

    fun setID(id: Long) {
        this.id = id
    }

    fun getID(): Long {
        return id
    }

    fun loadProgram(data: List<Byte>) {
        if (!romMode) {
            throw RuntimeException("Cannot load programs in RAM mode")
        }
        this.data.clear()
        this.data.addAll(0, data)
        calculateValueRegisters()
    }

    fun createRamMemory(characters: Int, statusCharacters: Int, registers: Int) {
        if (romMode) {
            throw RuntimeException("Cannot create memory in ROM mode")
        }
        this.data       = MutableList(characters * registers) { 0.toByte() }
        this.statusMem  = MutableList(statusCharacters * registers) { 0.toByte() }
        this.registers  = registers.toLong()
        this.characters = characters.toLong()
        this.statusCharacters = statusCharacters.toLong()
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
                if (extBus.value == IO.toLong().shr(4).and(0xf)) {
                    if (srcDetected) {
                        log.debug("ROM/RAM: IO instruction detected")
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
                    log.debug("ROM/RAM: FIM/SRC instruction detected")
                    srcDetected = true
                } else {
                    srcDetected = false
                }

                instReg.writeNybbleDirect(1, extBus.value)
            }
            4 -> {
                bufDir = BufDirIn   // Transfer to the internal bus
                instReg.writeNybbleDirect(0, extBus.value)
                // NOTE: FIM and SRC have the same upper 4 bits
                if (extBus.value.and(1) == 1L) {
                    log.debug("ROM/RAM: SRC instruction detected")
                } else {
                    log.debug("ROM/RAM: FIM instruction detected")
                    srcDetected = false
                }

            }
            5 -> {
                bufDir = BufDirIn   // Transfer to the internal bus
                // IO ops that write data to the external data bus go here
                if (ioOpDetected) {
                    val cmd = instReg.readDirect()
                    when (cmd) {
                        RDR.toLong().and(0xff) -> {
                            if (romMode) {
                                bufDir = BufDirOut  // Transfer to the external bus
                                ioRead = true
                            }
                        }
                        RDM.toLong().and(0xff),SBM.toLong().and(0xff),ADM.toLong().and(0xff) -> {
                            if (!romMode) {
                                bufDir = BufDirOut  // Transfer to the external bus
                                memRead = true
                            }
                        }
                        RD0.toLong().and(0xff),RD1.toLong().and(0xff),RD2.toLong().and(0xff),RD3.toLong().and(0xff) -> {
                            if (!romMode) {
                                bufDir = BufDirOut  // Transfer to the external bus
                                statusRead = cmd - RD0.toInt().and(0xff)
                            }
                        }
                    }
                }
            }
            6 -> {
                if (srcDetected) {
                    bufDir = BufDirIn   // Transfer to the internal bus
                    if (romMode) {
                        srcDeviceID = intBus.read().and(0xf)
                        if (srcDeviceID != id) {
                            log.debug(
                                String.format(
                                    "ROM/RAM: SRC command was NOT for us. Our chipID=%02X, cmd chipID=%02X",
                                    id, srcDeviceID
                                )
                            )
                            srcDetected = false
                        } else {
                            log.debug(String.format("ROM/RAM: SRC command WAS for us. Our chipID=%02X", id))
                        }
                    } else {
                        // RAM mode
                        // The upper two bits are the chip ID (4 RAMs/CM RAM line)
                        srcDeviceID = intBus.read().and(0xC).shr(2)
                        srcRegisterSel = intBus.read().and(0x3)
                        if (srcDeviceID != id) {
                            log.debug(
                                String.format(
                                    "ROM/RAM: SRC command was NOT for us. Our chipID=%02X, cmd chipID=%02X",
                                    id, srcDeviceID
                                )
                            )
                            srcDetected = false
                        } else {
                            log.debug(String.format("ROM/RAM: SRC command WAS for us. Our chipID=%02X", id))
                        }
                    }
                }
                // IO ops that get data from the external data bus go here
                if (ioOpDetected) {
                    val cmd = instReg.readDirect()
                    when (cmd) {
                        WRM.toLong().and(0xff) -> {
                            if (!romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // Write to the requested memory character
                                if ((srcCharacterSel+(srcRegisterSel*characters)) >= 0 && (srcCharacterSel+(srcRegisterSel*characters)) < data.size) {
                                    data[(srcCharacterSel+(srcRegisterSel*characters)).toInt()] = intBus.read().toByte()
                                }
                            }
                        }
                        WR0.toLong().and(0xff),WR1.toLong().and(0xff),WR2.toLong().and(0xff),WR3.toLong().and(0xff) -> {
                            if (!romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // Write to the requested status character
                                val index = cmd - WR0.toLong().and(0xff)
                                if ((index+(srcRegisterSel*statusCharacters)) >= 0 && (index+(srcRegisterSel*statusCharacters)) < statusMem.size) {
                                    statusMem[(index+(srcRegisterSel*statusCharacters)).toInt()] = intBus.read().toByte()
                                }
                            }
                        }

                        WMP.toLong().and(0xff) -> {
                            if (!romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // IO Write
                                ioBus.reset()
                                ioBus.write(intBus.read())
                            }
                        }
                        WRR.toLong().and(0xff) -> {
                            if (romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // IO Write
                                ioBus.reset()
                                ioBus.write(intBus.read())
                            }
                        }
                    }
                }
            }
            7 -> {
                if (!romMode) {
                    srcCharacterSel = intBus.read().and(0xf)
                }
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
            if (addrReg.readDirect().toInt() < data.size) {
                val outData = data[addrReg.readDirect().toInt()].toLong().shr(4).and(0xf)
                intBus.write(outData)
            }
        } else if (romDataOut == 2) {
            if (addrReg.readDirect().toInt() < data.size) {
                val outData = data[addrReg.readDirect().toInt()].toLong().and((0xf))
                intBus.write(outData)
            }
        }
        if (ioRead) {
            val outData = ioBus.value
            intBus.write(outData)
        }
        if (memRead) {
            if ((srcCharacterSel+(srcRegisterSel*characters)) >= 0 && (srcCharacterSel+(srcRegisterSel*characters)) < data.size) {
                val outData = data[(srcCharacterSel+(srcRegisterSel*characters)).toInt()].toLong()
                intBus.write(outData)
            }
        }
        if (statusRead >= 0) {
            if ((statusRead+(srcRegisterSel*statusCharacters)) >= 0 && (statusRead+(srcRegisterSel*statusCharacters)) < statusMem.size) {
                val outData = statusMem[(statusRead+(srcRegisterSel*statusCharacters)).toInt()].toLong()
                intBus.write(outData)
            }
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
        if ((first+2) >= data.size) {
            return
        }

        valueRegs[0].writeDirect(data[first].toLong())
        valueRegs[1].writeDirect(data[first+1].toLong())
        valueRegs[2].writeDirect(data[first+2].toLong())
    }


}