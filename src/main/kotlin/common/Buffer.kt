package common

class Buffer(val busA: Bus, val busB: Bus, val name: String) {
    // Transfer data from bus A to B
    fun aToB() {
        busB.write(busA.value)
    }
    // Transfer data from bus B to A
    fun bToA() {
        busA.write(busB.value)
    }
}