package common

import java.awt.Color

val Margin                  = 16
val Background              = Color(0xC0C0C0)
val RegisterBorder          = Color(0xD0D0D0)
val RegisterBackground      = Color(0x202020)
val RegisterBackgroundSel   = Color(0x606060)
val RegisterTextNormal      = Color(0xFFFFFF)
val TextNormal              = Color(0x000000)
val BusBackground           = Color(0x606060)
val BusBackgroundDriving    = Color(0x40A040)
val BufArrowColor           = Color(0x40f040)
val AluFill                 = Color(0x00A0A0)
val LedRedOn                = Color(0xF02020)
val LedRedOff               = Color(0x700000)
val LedRedBorder            = Color(0xF08080)

val MainFont                = "Monospaced"
val MainFontSize            = 24
val RegisterWidth           = 110
val RegisterHeight          = 40
val BusWidth                = 4     // this is a 4 bit processor

const val BufDirNone = 0    // No transfer between busses
const val BufDirOut = 1     // Transfer from internal bus to external bus
const val BufDirIn = 2      // Transfer from external bus to internal bus
const val BufDirAtoB = 1    // Transfer from A bus to B bus
const val BufDirBtoA = 2    // Transfer from B bus to A bus


