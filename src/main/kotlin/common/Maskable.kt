package common

open class Maskable {
    var width = 0
    var mask = 0UL
    open var name = ""
    fun baseInit(width: Int, name: String) {
        this.width = width
        for (i in 0 until width) {
            this.mask = this.mask.shl(1)
            this.mask = this.mask.or(1U)
        }
        this.name = name
    }
}