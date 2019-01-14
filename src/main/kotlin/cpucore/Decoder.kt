package cpucore

import common.*
import io.reactivex.Observable
import utils.logger
import kotlin.experimental.and
import kotlin.experimental.or

class Decoder(clk: Observable<Int>) {
    val log = logger()

    val clkCount = Clocked(0, clk)
    val flags = mutableMapOf<FlagTypes, DecoderFlag>()
    var syncSent = false
    var currInstruction = -1     // If >= 0, the current instruction we are processing
    var dblInstruction = 0       // If > 0, this is the current double instruction we are processing
    var decodeAgain = false      // Set when an instruction needs another decode cycle
    var inhibitPCInc = false     // Block the program counter from incrementing
    var inhibitPC = false        // Don't output program counter on the bus
    var decodedInstruction = ""
    var x2IsRead = false         // The X2 cycle is a read from the external bus
    var x3IsRead = false         // The X3 cycle is a read from the external bus
    var instChanged = false      // For the renderer

    init {
        createFlags()
    }

    fun createFlags() {
        flags[FlagTypes.Sync]           = DecoderFlag("SYNC", 0, false)
        flags[FlagTypes.BusDir]         = DecoderFlag("BDIR", BufDirNone, false)
        flags[FlagTypes.BusTurnAround]  = DecoderFlag("BTA ", 0, false)
        flags[FlagTypes.InstRegOut]     = DecoderFlag("INSO  ", 0, false)
        flags[FlagTypes.InstRegLoad]    = DecoderFlag("INSL  ", 0, false)
        flags[FlagTypes.PCOut]          = DecoderFlag("PCO ", 0, false)
        flags[FlagTypes.PCLoad]         = DecoderFlag("PCL ", 0, false)
        flags[FlagTypes.PCInc]          = DecoderFlag("PCI ", 0, false)
        flags[FlagTypes.AccOut]         = DecoderFlag("ACCO  ", 0, false)
        flags[FlagTypes.AccLoad]        = DecoderFlag("ACCL  ", 0, false)
        flags[FlagTypes.AccInst]        = DecoderFlag("ACCI  ", -1, false)
        flags[FlagTypes.TempOut]        = DecoderFlag("TMPO  ", 0, false)
        flags[FlagTypes.TempLoad]       = DecoderFlag("TMPL  ", 0, false)
        flags[FlagTypes.AccTempSwap]    = DecoderFlag("SWAP  ", 0, false)
        flags[FlagTypes.AluOut]         = DecoderFlag("ALUO", 0, false)
        flags[FlagTypes.AluEval]        = DecoderFlag("ALUE", 0, false)
        flags[FlagTypes.AluMode]        = DecoderFlag("ALUM", 0, false)
        flags[FlagTypes.IndexSelect]    = DecoderFlag("SPI ", -1, false)
        flags[FlagTypes.IndexLoad]      = DecoderFlag("SPL4", 0, false)
        flags[FlagTypes.ScratchPadOut]  = DecoderFlag("SPO ", 0, false)
        flags[FlagTypes.ScratchPadInc]  = DecoderFlag("SP+ ", 0, false)
        flags[FlagTypes.StackPush]      = DecoderFlag("PUSH", 0, false)
        flags[FlagTypes.StackPop]       = DecoderFlag("POP ", 0, false)
        flags[FlagTypes.DecodeInstruction] = DecoderFlag("DEC ", 0, false)
        flags[FlagTypes.CmRom]          = DecoderFlag("ROM ", 0, false)
        flags[FlagTypes.CmRam]          = DecoderFlag("RAM ", 0, false)
        flags[FlagTypes.EvaluateJCN]    = DecoderFlag("EJCN", 0, false)
        flags[FlagTypes.EvaluateISZ]    = DecoderFlag("EISZ", 0, false)
    }

    fun resetFlags() {
        for (flag in flags) {
            flag.value.reset()
        }
        x2IsRead = false
        x3IsRead = false
    }

    fun writeFlag(id: FlagTypes, value: Int) {
        val flag = flags[id]
        flag!!.writeFlag(value)
    }

    fun readFlag(id: FlagTypes): Int {
        return flags[id]?.value ?: 0    // 'Elvis' operator. Return 0 if flag is null
    }

    fun clkAndSync() {
        when (clkCount.clocked) {
            6 -> {
                if (syncSent && !inhibitPCInc)
                    writeFlag(FlagTypes.PCInc, 1)
                clkCount.raw = clkCount.clocked + 1
            }
            7 -> {
                clkCount.raw = 0
            }
            else -> {
                clkCount.raw = clkCount.clocked + 1
            }
        }
    }

    fun calculateFlags(intBus: Bus) {
        // Continue to decode instructions after clock 5
        if (clkCount.raw != 5 && !decodeAgain && currInstruction > 0) {
            decodeCurrentInstruction(false)
        }

        // NOTE: we are using the raw count here. This allows us to use the next clock cycle count
        // as our index. That way everything is not off by one
        when (clkCount.raw) {
            0 -> {
                if (!inhibitPC)
                    writeFlag(FlagTypes.PCOut, 1)   // Output nybble 0
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            1 -> {
                if (!inhibitPC)
                    writeFlag(FlagTypes.PCOut, 2)   // Output nybble 1
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            2 -> {
                if (!inhibitPC)
                    writeFlag(FlagTypes.PCOut, 3)   // Output nybble 2
                writeFlag(FlagTypes.CmRom, 1)   // Enable the ROM(s)
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            3 -> {
                writeFlag(FlagTypes.BusDir, BufDirIn) // Transfer to the internal bus
                // We need to check for IO ops here. We have not run the decode yet.
                // but we need to assert the CM* lines
                if (intBus.value == IO.toLong().shr(4)) {
                    writeFlag(FlagTypes.CmRom, 1)
                    writeFlag(FlagTypes.CmRam, 1)
                }
            }
            4 -> {
                writeFlag(FlagTypes.InstRegLoad, 2) // load the upper nybble of the inst register
                writeFlag(FlagTypes.BusDir, BufDirIn) // Transfer to the internal bus
                if (intBus.value == IO.toLong().shr(4).and(0xf)) {
                    writeFlag(FlagTypes.CmRom, 1)
                    writeFlag(FlagTypes.CmRam, 1)
                }
            }
            5 -> {
                writeFlag(FlagTypes.InstRegLoad, 1) // load the lower nybble of the inst register
                writeFlag(FlagTypes.DecodeInstruction, 1) // Decode the instruction register
//                writeFlag(FlagTypes.BusDir, BufDirIn)// Transfer to the external bus
                writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
            6 -> {
                if (decodeAgain) {
                    writeFlag(FlagTypes.DecodeInstruction, 1) // Decode the instruction register
                    decodeAgain = false
                }
                if (x2IsRead)
                    writeFlag(FlagTypes.BusDir, BufDirIn) // Transfer to the internal bus
                else {
                    writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
                }
            }
            7 -> {
                writeFlag(FlagTypes.Sync, 1)    // Generate the sync pulse
                syncSent = true
                if (x3IsRead)
                    writeFlag(FlagTypes.BusDir, BufDirIn) // Transfer to the internal bus
                else
                    writeFlag(FlagTypes.BusDir, BufDirOut)// Transfer to the external bus
            }
        }
    }

    fun setCurrentInstruction(inst: Long, evalResult: Boolean) {
        if (dblInstruction == 0) {
            if (inst != 0L) {
                log.debug(String.format("SetCurrentInstruction: %02X", inst))
            }
            currInstruction = inst.toInt()
        } else {
            currInstruction = dblInstruction
        }
        decodeCurrentInstruction(evalResult)
    }

    fun decodeCurrentInstruction(evalResult: Boolean) {
        // The upper 4 bits of the instruction
        val opr = currInstruction.and(0xf0).toByte()
        val fullInst = currInstruction.toByte()
        when (opr) {
            // Note FIN and JIN share the same upper 4 bits
            FIN.toInt().and(0xf0).toByte() ->
                handleFIN_JIN(this, fullInst.toLong())
            JCN, JMS, ISZ, JUN ->
                handleJCN_JMS_ISZ_JUN(this, fullInst.toLong(), evalResult)
            XCH ->
                handleXCH(this)
            LDM ->
                handleLDM(this)
            LD ->
                handleLD(this)
            INC ->
                handleINC(this)
            FIM, SRC ->
                handleFIM_SRC(this, fullInst.toLong())
            BBL ->
                handleBBL(this)
            ADD ->
                handleADD(this, fullInst.toLong())
            SUB ->
                handleSUB(this, fullInst.toLong())
            // Collectively, all the accumulator instructions
            ACC ->
                handleACC(this)
            WRR.and(0xF0.toByte()), RDR.and(0xF0.toByte()) ->
                decodeAgain = true
        }

        // These instructions require decoding the entire 8 bits
        when (fullInst) {
            WMP ->
                handleWMP(this)
            WRR ->
                handleWRR(this)
        }
    }

    fun setDecodedInstructionString(inst: String) {
        decodedInstruction = inst
        instChanged = true
        if (log.isDebugEnabled)
            log.debug(String.format("--- Decoded instruction is: %s", decodedInstruction))
    }

}

