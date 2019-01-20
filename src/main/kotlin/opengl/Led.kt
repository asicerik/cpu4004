package opengl

import org.joml.Vector3f

class LedGl {
    val v = arrayListOf<Float>()
    val c = arrayListOf<Float>()
    var segments = 0
    var on = false
    var changed = true
    var obj = GlObject()
    fun createLedVbo(center: Vector3f, radius: Float, segments: Int): Pair<ArrayList<Float>, ArrayList<Float>> {
        this.segments = segments
        val delta = 2 * Math.PI / segments
        for (i in 0 until segments) {
            v.addAll(arrayListOf(center.x(), center.y(), center.z()))
            v.addAll(
                arrayListOf(
                    center.x() + radius * Math.sin(i * delta).toFloat(),
                    center.y() + radius * Math.cos(i * delta).toFloat(), center.z()
                )
            )
            v.addAll(
                arrayListOf(
                    center.x() + radius * Math.sin((i + 1) * delta).toFloat(),
                    center.y() + radius * Math.cos((i + 1) * delta).toFloat(), center.z()
                )
            )
            c.addAll(arrayListOf(0f,0f,0f))
            c.addAll(arrayListOf(0f,0f,0f))
            c.addAll(arrayListOf(0f,0f,0f))
        }
        set(false)
        return Pair(v, c)
    }
    fun set(on: Boolean) {
        if (c.isEmpty()) {
            return
        }
        changed = (on != this.on) || changed
        this.on = on
        if (changed) {
            if (on) {
                for (i in 0 until segments) {
                    val base = i * 9
                    setVector(c.subList(base + 0, base + 3), Vector3f(0.99f, 0.05f, 0.05f))
                    setVector(c.subList(base + 3, base + 6), Vector3f(0.65f, 0.05f, 0.05f))
                    setVector(c.subList(base + 6, base + 9), Vector3f(0.65f, 0.05f, 0.05f))
                }
            } else {
                for (i in 0 until segments) {
                    val base = i * 9
                    setVector(c.subList(base + 0, base + 3), Vector3f(0.35f, 0.05f, 0.05f))
                    setVector(c.subList(base + 3, base + 6), Vector3f(0.15f, 0.05f, 0.05f))
                    setVector(c.subList(base + 6, base + 9), Vector3f(0.15f, 0.05f, 0.05f))
                }
            }
        }
    }
}