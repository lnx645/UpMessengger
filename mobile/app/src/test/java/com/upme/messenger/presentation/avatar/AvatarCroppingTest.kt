package com.upme.messenger.presentation.avatar

import org.junit.Test
import org.junit.Assert.*

/**
 * UserAvatarCroppingFragment harus ada sebagai fragment khusus crop avatar,
 * reusable, WA-style, dipakai di SetupProfil.
 */
class AvatarCroppingTest {

    @Test fun fragmentExists() {
        val clazz = Class.forName("com.upme.messenger.presentation.avatar.UserAvatarCroppingFragment")
        assertNotNull(clazz)
        assertTrue(androidx.fragment.app.Fragment::class.java.isAssignableFrom(clazz))
    }

    @Test fun overlayExists() {
        val clazz = Class.forName("com.upme.messenger.presentation.avatar.CropOverlayView")
        assertNotNull(clazz)
        assertTrue(android.view.View::class.java.isAssignableFrom(clazz))
    }

    @Test fun fragmentHasNewInstanceAndKeys() {
        val candidates = listOf(
            java.io.File("src/main/java/com/upme/messenger/presentation/avatar/UserAvatarCroppingFragment.kt"),
            java.io.File("app/src/main/java/com/upme/messenger/presentation/avatar/UserAvatarCroppingFragment.kt"),
            java.io.File("/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/presentation/avatar/UserAvatarCroppingFragment.kt")
        )
        val f = candidates.firstOrNull { it.exists() } ?: candidates.last()
        assertTrue("UserAvatarCroppingFragment.kt harus ada", f.exists())
        val src = f.readText()
        assertTrue(src.contains("REQUEST_KEY"))
        assertTrue(src.contains("KEY_URI"))
        assertTrue(src.contains("fun newInstance"))
        assertTrue(src.contains("setFragmentResult"))
        // harus support pinch zoom & drag
        assertTrue(src.contains("Scale") || src.contains("ZOOM") || src.contains("spacing"))
        assertTrue(src.contains("Matrix") || src.contains("matrix"))
    }

    @Test fun setupProfilUsesCropping() {
        val candidates = listOf(
            java.io.File("src/main/java/com/upme/messenger/presentation/auth/SetupProfilFragment.kt"),
            java.io.File("app/src/main/java/com/upme/messenger/presentation/auth/SetupProfilFragment.kt"),
            java.io.File("/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/presentation/auth/SetupProfilFragment.kt")
        )
        val f = candidates.firstOrNull { it.exists() } ?: candidates.last()
        val src = f.readText()
        assertTrue(src.contains("UserAvatarCroppingFragment"))
        assertTrue(src.contains("avatarContainer") || src.contains("imgAvatar"))
        assertTrue(src.contains("pickLauncher") || src.contains("GetContent"))
        assertTrue(src.contains("setFragmentResultListener"))
    }

    @Test fun layoutCroppingExists() {
        val candidates = listOf(
            java.io.File("src/main/res/layout/fragment_user_avatar_cropping.xml"),
            java.io.File("app/src/main/res/layout/fragment_user_avatar_cropping.xml"),
            java.io.File("/home/demo/upchat/mobile/app/src/main/res/layout/fragment_user_avatar_cropping.xml")
        )
        val f = candidates.firstOrNull { it.exists() } ?: candidates.last()
        assertTrue("fragment_user_avatar_cropping.xml harus ada", f.exists())
        val src = f.readText()
        assertTrue(src.contains("imgCrop"))
        assertTrue(src.contains("CropOverlayView"))
        assertTrue(src.contains("btnSimpan"))
        assertTrue(src.contains("btnBatal"))
    }
}
