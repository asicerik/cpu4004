package common

class Buffer(val busA: Bus, val busB: Bus, val name: String) {
    var dir = BufDirNone

    // Transfer data from bus A to B
    fun aToB() {
        busB.write(busA.value)
        dir = BufDirAtoB
    }

    // Transfer data from bus B to A
    fun bToA() {
        busA.write(busB.value)
        dir = BufDirBtoA
    }

    // Set the bus direction without transferring data. This is for the renderer
    fun setBusDirectionAToB() {
        dir = BufDirAtoB
    }

    // Set the bus direction without transferring data. This is for the renderer
    fun setBusDirectionBtoA() {
        dir = BufDirBtoA
    }

    fun notifyBusWrite(side: String, value: ULong) {
        if (side == "A" && dir == BufDirAtoB) {
            busB.write(value)
        } else if (side == "B" && dir == BufDirBtoA) {
            busA.write(value)
        }
    }
}