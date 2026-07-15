package islanddev.scene

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapInputPolicyTest {
    @Test
    fun closedModalAcceptsMapTap() {
        assertTrue(
            MapInputPolicy.acceptsMapTap(
                tapMoveEnabled = true,
                isModalOpen = false,
                suppressTap = false,
                isHudTouch = false
            )
        )
    }

    @Test
    fun openModalRejectsMapTap() {
        assertFalse(
            MapInputPolicy.acceptsMapTap(
                tapMoveEnabled = true,
                isModalOpen = true,
                suppressTap = false,
                isHudTouch = false
            )
        )
    }

    @Test
    fun closingTapAndHudTouchAreRejected() {
        assertFalse(
            MapInputPolicy.acceptsMapTap(
                tapMoveEnabled = true,
                isModalOpen = false,
                suppressTap = true,
                isHudTouch = false
            )
        )
        assertFalse(
            MapInputPolicy.acceptsMapTap(
                tapMoveEnabled = true,
                isModalOpen = false,
                suppressTap = false,
                isHudTouch = true
            )
        )
    }

    @Test
    fun disabledTapMoveRejectsMapTap() {
        assertFalse(
            MapInputPolicy.acceptsMapTap(
                tapMoveEnabled = false,
                isModalOpen = false,
                suppressTap = false,
                isHudTouch = false
            )
        )
    }
}
