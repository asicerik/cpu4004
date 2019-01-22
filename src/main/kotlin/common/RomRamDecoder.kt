package common

import cpucore.*
import io.reactivex.Observable
import utils.logger

@kotlin.ExperimentalUnsignedTypes
open class RomRamDecoder(val extBus: Bus, val ioBus: Bus?, clk: Observable<Int>, val sync: Clocked<Int>, val cm: Clocked<Int>) {
    val log = logger()

    val clkCount = Clocked<UInt>(0U, clk)
    val addrReg = Register(0U, clk)
    val instReg = Register(0U, clk)
    val valueRegs = mutableListOf<Register>()
    val intBus = Bus()
    val buffer = Buffer(intBus, extBus, "I/O Buf ")
    var data = mutableListOf<UByte>()
    var statusMem = mutableListOf<UByte>()   // For RAM only
    var registers  = 0                      // How many registers (banks) of memory in RAM
    var characters = 0                      // How many characters per bank (register)
    var statusCharacters = 0                // How many status characters per bank (register)

    // Renderer stuff
    var drivingBus = false

    var romMode = true              // Chip is a ROM by default
    var omniMode = false            // If true, we respond for all device accesses (so we don't need lots of devices)
    private var id = 0              // Which chip are we?

    // Flags
    var syncSeen = false
    var bufDir = BufDirNone     // Which direction (if any) to transfer between internal and external bus
    var addrLoad = 0            // Load the address register. The value is the nybble (1 based)
    var romDataOut = 0          // Output the ROM data. The value is the nybble (1 based)
    var ioRead = false          // If true, output our IO bus data to the data bus
    var memRead = false         // If true, output the selected RAM character
    var statusRead = -1         // If >=0, output the selected RAM status character
    var chipSelected = false
    var srcDetected  = false    // SRC command was detected
    var srcDeviceID     = 0     // The ROM/RAM ID sent in the SRC command
    var srcRegisterSel  = 0     // The RAM register select sent in the SRC command
    var srcCharacterSel = 0     // The RAM character select sent in the SRC command
    var ioOpDetected = false    // IO Operation was detected

    init {
        intBus.init(4, "ROM Internal Bus")
        addrReg.init(intBus, 12, "Addr ")
        instReg.init(intBus, 8, "INST ")
        for (i in 0..2) {
            valueRegs.add(Register(0U, clk))
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
        statusRead  = -1
        chipSelected= false
        srcDetected = false
        srcDeviceID    = 0
        ioOpDetected= false
        drivingBus  = false
        for (i in 0 until data.size) {
            data[i] = 0U
        }
    }

    fun setID(id: Int) {
        this.id = id
    }

    fun getID(): Int {
        return id
    }

    fun loadProgram(data: List<UByte>) {
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
        this.data       = MutableList(characters * registers) { 0.toUByte() }
        this.statusMem  = MutableList(statusCharacters * registers) { 0.toUByte() }
        this.registers  = registers
        this.characters = characters
        this.statusCharacters = statusCharacters
    }

    fun resetFlags() {
        addrLoad = 0
        romDataOut = 0
        memRead = false
        statusRead = -1

        // Transfer to the internal data bus if needed so the data is available to the decoder
        if (bufDir == BufDirIn) {
            buffer.bToA()
        }
        calculateValueRegisters()
    }

    fun clkAndSync() {
        val a = sync.clocked
        val b = clkCount.clocked
        val aC = a == 0
        val bC = b == 7U
        if (aC || bC) {
            clkCount.raw = 0U
            syncSeen = true
        } else if (syncSeen) {
            clkCount.raw = clkCount.clocked + 1U
        }
    }

    fun calculateFlags() {
        if (!syncSeen) {
            return
        }

        when (clkCount.clocked) {
            0U -> {
                addrLoad = 1
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            1U -> {
                addrLoad = 2
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            2U -> {
                // This is a little bit of a hack. We are looking at the external bus here.
                // This is because it is really hard to model the bus buffers in real-time.
                chipSelected = ((extBus.value.toInt() == id) || omniMode) && (cm.clocked == 0)
                if (log.isDebugEnabled)
                    log.debug("Chip ID {} ROM={}: selected = {}, ADDR={}", id, romMode, chipSelected, addrReg.readDirect())
                if (chipSelected && romMode) {
                    romDataOut = 1
                    bufDir = BufDirOut  // Transfer to the external bus
                } else {
                    bufDir = BufDirIn   // Transfer to the internal bus
                }

            }
            3U -> {
                if (chipSelected && romMode) {
                    romDataOut = 2
                    bufDir = BufDirOut  // Transfer to the external bus
                }
                // Check for IO ops before we update the SRC flag
                if (extBus.value.toUInt() == IO.shr(4).and(0xfU)) {
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
                if (extBus.value.toUInt() == SRC.shr(4).and(0xfU)) {
                    log.debug("ROM/RAM: FIM/SRC instruction detected")
                    srcDetected = true
                } else {
                    srcDetected = false
                }

                instReg.writeNybbleDirect(1, extBus.value)
            }
            4U -> {
                bufDir = BufDirIn   // Transfer to the internal bus
                instReg.writeNybbleDirect(0, extBus.value)
                // NOTE: FIM and SRC have the same upper 4 bits
                if (srcDetected) {
                    if (extBus.value.and(1U) == 1UL) {
                        log.debug("ROM/RAM: SRC instruction detected")
                    } else {
                        log.debug("ROM/RAM: FIM instruction detected")
                        srcDetected = false
                    }
                }
            }
            5U -> {
                bufDir = BufDirIn   // Transfer to the internal bus
                // IO ops that write data to the external data bus go here
                if (ioOpDetected) {
                    val cmd = instReg.readDirect().toUInt()
                    when (cmd) {
                        RDR.and(0xffU) -> {
                            if (romMode) {
                                bufDir = BufDirOut  // Transfer to the external bus
                                ioRead = true
                            }
                        }
                        RDM.and(0xffU),SBM.and(0xffU),ADM.and(0xffU) -> {
                            if (!romMode) {
                                bufDir = BufDirOut  // Transfer to the external bus
                                memRead = true
                            }
                        }
                        RD0.and(0xffU),RD1.and(0xffU),RD2.and(0xffU),RD3.and(0xffU) -> {
                            if (!romMode) {
                                bufDir = BufDirOut  // Transfer to the external bus
                                statusRead = (cmd - RD0.and(0xffU)).toInt()
                            }
                        }
                    }
                }
            }
            6U -> {
                if (srcDetected) {
                    bufDir = BufDirIn   // Transfer to the internal bus
                    if (romMode) {
                        srcDeviceID = intBus.read().and(0xfU).toInt()
                        if (omniMode) {
                            log.debug(String.format("ROM/RAM: Omni mode is on. Responding to all SRC commands"))
                        } else if (srcDeviceID != id) {
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
                        srcDeviceID = intBus.read().and(0xcU).shr(2).toInt()
                        srcRegisterSel = intBus.read().and(0x3U).toInt()
                        if (omniMode) {
                            log.debug(String.format("ROM/RAM: Omni mode is on. Responding to all SRC commands"))
                        } else if (srcDeviceID != id) {
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
                    val cmd = instReg.readDirect().toUInt()
                    when (cmd) {
                        WRM.and(0xffU) -> {
                            if (!romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // Write to the requested memory character
                                if ((srcCharacterSel+(srcRegisterSel*characters)) >= 0 && (srcCharacterSel+(srcRegisterSel*characters)) < data.size) {
                                    data[(srcCharacterSel+(srcRegisterSel*characters))] = intBus.read().toUByte()
                                    addrReg.writeDirect((srcCharacterSel+(srcRegisterSel*characters)).toULong())
                                    calculateValueRegisters()
                                    log.debug(String.format("Wrote RAM memory %d with %X. Our chipID=%02X",
                                        (srcCharacterSel+(srcRegisterSel*characters)),
                                        intBus.read(),
                                        id))
                                }
                            }
                        }
                        WR0.and(0xffU),WR1.and(0xffU),WR2.and(0xffU),WR3.and(0xffU) -> {
                            if (!romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // Write to the requested status character
                                val index = cmd.toInt() - WR0.and(0xffU).toInt()
                                if ((index+(srcRegisterSel*statusCharacters)) >= 0 && (index+(srcRegisterSel*statusCharacters)) < statusMem.size) {
                                    statusMem[(index+(srcRegisterSel*statusCharacters))] = intBus.read().toUByte()
                                    log.debug(String.format("Wrote RAM status mem %d with %X. Our chipID=%02X",
                                        (index+(srcRegisterSel*statusCharacters)),
                                        intBus.read().toLong(),
                                        id))
                                }
                            }
                        }

                        WMP.and(0xffU) -> {
                            if (!romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // IO Write
                                ioBus?.reset()
                                if (omniMode && ioBus != null) {
                                    var currVal = ioBus.value
                                    val mask = 0xf.toULong().shl(srcDeviceID*4)
                                    currVal = currVal.and(mask.inv())
                                    currVal = currVal.or(intBus.read().and(0xfU).shl(srcDeviceID*4))
                                    ioBus.write(currVal)
                                    log.debug(String.format("Wrote RAM I/O %X. Our chipID=%02X", currVal.toLong(), srcDeviceID))
                                } else {
                                    ioBus!!.write(intBus.read())
                                }
                            }
                        }
                        WRR.and(0xffU) -> {
                            if (romMode) {
                                bufDir = BufDirIn   // Transfer to the internal bus
                                // IO Write
                                ioBus?.reset()
                                if (omniMode && ioBus != null) {
                                    var currVal = ioBus.value
                                    val mask = 0xf.toULong().shl(srcDeviceID*4)
                                    currVal = currVal.and(mask.inv())
                                    currVal = currVal.or(intBus.read().and(0xfU).shl(srcDeviceID*4))
                                    ioBus.write(currVal)
                                    log.debug(String.format("Wrote ROM I/O %X. Our chipID=%02X", currVal.toLong(), srcDeviceID.toLong()))
                                } else {
                                    ioBus!!.write(intBus.read())
                                }
                            }
                        }
                    }
                }
            }
            7U -> {
                if (!romMode) {
                    srcCharacterSel = intBus.read().and(0xfU).toInt()
                }
                bufDir = BufDirIn   // Transfer to the internal bus
            }
        }
    }

    fun update() {
        // Address register and chip select
        if (romMode) {
            if (addrLoad == 1) {
                addrReg.writeNybble(0)
            } else if (addrLoad == 2) {
                addrReg.writeNybble(1)
            }
        }

        if (romDataOut == 1) {
            if (addrReg.readDirect().toInt() < data.size) {
                val outData = data[addrReg.readDirect().toInt()].toULong().shr(4).and(0xfU)
                intBus.write(outData)
            }
        } else if (romDataOut == 2) {
            if (addrReg.readDirect().toInt() < data.size) {
                val outData = data[addrReg.readDirect().toInt()].toULong().and((0xfU))
                intBus.write(outData)
            }
        }
        if (ioRead && ioBus != null) {
            val outData = ioBus.value
            intBus.write(outData)
        }
        if (memRead) {
            if ((srcCharacterSel+(srcRegisterSel*characters)) >= 0 && (srcCharacterSel+(srcRegisterSel*characters)) < data.size) {
                val outData = data[(srcCharacterSel+(srcRegisterSel*characters))].toULong()
                intBus.write(outData)
                log.debug(String.format("Read RAM mem %d = %X. Our chipID=%02X",
                    (srcCharacterSel+(srcRegisterSel*characters)), outData.toLong(), id))
            }
        }
        if (statusRead >= 0) {
            if ((statusRead+(srcRegisterSel*statusCharacters)) >= 0 && (statusRead+(srcRegisterSel*statusCharacters)) < statusMem.size) {
                val outData = statusMem[(statusRead+(srcRegisterSel*statusCharacters))].toULong()
                intBus.write(outData)
                log.debug(String.format("Read RAM status mem %d = %X. Our chipID=%02X",
                    (srcCharacterSel+(srcRegisterSel*characters)), outData.toLong(), id))
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
        val curr = addrReg.readDirect().and(0xffU).toInt()
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

        valueRegs[0].writeDirect(data[first].toULong())
        valueRegs[1].writeDirect(data[first+1].toULong())
        valueRegs[2].writeDirect(data[first+2].toULong())
    }


}