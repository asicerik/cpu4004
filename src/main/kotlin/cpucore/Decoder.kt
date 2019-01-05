package cpucore

import common.Clocked
import io.reactivex.Observable

const val BufDirNone = 0    // No transfer between busses
const val BufDirOut = 1     // Transfer from internal bus to external bus
const val BufDirIn = 1      // Transfer from external bus to internal bus

class Decoder(clk: Observable<Int>) {

    val clkCount = Clocked(0, clk)
    var incPC = 0       // Increment the program counter
    var genSync = 0     // Generate a sync pulse
    var genCmRom = 0    // Output the CMROM signal
    var genCmRam = 0    // Output one of the CMRAM signals (4 bits)
    var pcOut = 0       // > 0 = Output the program counter. The number will be the PC nybble to output (1 based)
    var bufDir = BufDirNone // Which direction (if any) to transfer between internal and external bus
    var instLoad = 0    // Load the instruction register. The value will be the nybble (1 based)
    var decode = 0      // Decode the instruction register
    var syncSent = false

    fun resetFlags() {
        genSync = 0
        incPC = 0
        pcOut = 0
        genCmRam = 0
        genCmRom = 0
        bufDir = BufDirNone
        instLoad = 0
        decode = 0
    }

    fun clkAndSync() {
        when (clkCount.clocked) {
            6 -> {
                if (syncSent)
                    incPC = 1
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

    fun calculateFlags() {
        // NOTE: we are using the raw count here. This allows us to use the next clock cycle count
        // as our index. That way everything is not off by one
        when (clkCount.raw) {
            0 -> {
                pcOut = 1           // Output nybble 0
                bufDir = BufDirOut  // Transfer to the external bus
            }
            1 -> {
                pcOut = 2           // Output nybble 1
                bufDir = BufDirOut  // Transfer to the external bus
            }
            2 -> {
                pcOut = 3           // Output nybble 2
                genCmRom = 1        // Enable the ROM(s)
                bufDir = BufDirOut  // Transfer to the external bus
            }
            3 -> {
                instLoad = 2        // load the upper nybble of the inst register
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            4 -> {
                instLoad = 1        // load the lower nybble of the inst register
                decode = 1          // Decode the instruction register
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            5 -> {
                bufDir = BufDirOut  // Transfer to the external bus
            }
            6 -> {
                // Could be in or out
                bufDir = BufDirIn   // Transfer to the internal bus
            }
            7 -> {
                genSync = 1         // Generate the sync pulse
                syncSent = true
                bufDir = BufDirOut  // Transfer to the external bus
            }
        }
    }


}