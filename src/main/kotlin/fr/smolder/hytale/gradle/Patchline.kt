package fr.smolder.hytale.gradle

class Patchline(val value: String) {
    companion object {
        val RELEASE = Patchline("release")
        val PRE_RELEASE = Patchline("pre-release")
    }
    
    override fun toString(): String = value
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Patchline) return false
        return value == other.value
    }
    
    override fun hashCode(): Int = value.hashCode()
}
