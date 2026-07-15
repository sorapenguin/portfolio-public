package islanddev.scene

object MapInputPolicy {
    fun acceptsMapTap(
        tapMoveEnabled: Boolean,
        isModalOpen: Boolean,
        suppressTap: Boolean,
        isHudTouch: Boolean
    ): Boolean =
        tapMoveEnabled && !isModalOpen && !suppressTap && !isHudTouch
}
