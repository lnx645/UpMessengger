package com.upme.messenger.presentation.teman

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class TemanForceCloseTest {
  private val temanPathCandidates = listOf(
    "app/src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt",
    "src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt",
    "../app/src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt",
    "/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/presentation/friend/FriendFragment.kt",
    "app/src/main/java/com/upme/messenger/presentation/teman/TemanFragment.kt",
    "/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/presentation/teman/TemanFragment.kt"
  )
  private fun findTemanFile(): File = temanPathCandidates.map { File(it) }.firstOrNull { it.exists() } ?: File(temanPathCandidates[0])

  @Test fun refreshUsesLifecycleScopeNotViewLifecycleOwner() {
    val f = findTemanFile()
    assertTrue("TemanFragment.kt exists at ${temanPathCandidates}", f.exists())
    val txt = f.readText()
    // refresh() must not use viewLifecycleOwner before view
    assertTrue("refresh should use lifecycleScope", txt.contains("lifecycleScope.launch"))
    // should guard isAdded/view null
    assertTrue("refresh guards isAdded", txt.contains("if (!isAdded || view == null) return"))
    // should not have viewTreeObserver crash listener
    assertFalse("should not use addOnWindowFocusChangeListener with refresh", txt.contains("addOnWindowFocusChangeListener { hasFocus -> if (hasFocus) refresh()"))
  }

  @Test fun qrLauncherExists() {
    val f = findTemanFile()
    val txt = f.readText()
    assertTrue("qrLauncher should use ScanContract", txt.contains("ScanContract"))
    assertTrue("qrLauncher should be registered", txt.contains("qrLauncher"))
    assertTrue("btnQr should launch scanner", txt.contains("qrLauncher.launch"))
    assertFalse("QR should not be toast-only", txt.contains("QR Scan belum tersedia — masukkan PIN manual") && !txt.contains("ScanOptions"))
  }

  @Test fun hapusUsesConfirmDialog() {
    val f = findTemanFile()
    val txt = f.readText()
    assertTrue("hapus should use ConfirmDialog", txt.contains("ConfirmDialog"))
    assertTrue("hapus message contains Hapus teman", txt.contains("Hapus teman?"))
  }
}
