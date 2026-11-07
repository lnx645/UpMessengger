package com.upme.messenger.presentation.auth

import org.junit.Test
import org.junit.Assert.*

/**
 * Regression for: long-press delete harus hapus berantai 1 per 1 sampai habis (WA-style),
 * bukan sekali hapus satu atau clear sekaligus.
 * Juga: konfirmasi ganti nomor harus pakai ConfirmDialog reusable bukan AlertDialog bawaan.
 */
class OtpChainDeleteTest {

  // Simulasi logic chain delete yang ada di OtpFragment.startChainDelete
  private fun chainDeleteStep(boxes: MutableList<String>): Int {
    // cari kotak terakhir yang terisi (sesuai OtpFragment: loop 5 downTo 0)
    var targetIdx = -1
    for (i in 5 downTo 0) {
      if (boxes[i].isNotEmpty()) { targetIdx = i; break }
    }
    if (targetIdx == -1) return -1
    boxes[targetIdx] = ""
    return targetIdx
  }

  @Test fun chainDelete_hapusBerantaiSatuPerSatu() {
    val boxes = mutableListOf("1","2","3","4","5","6")
    val order = mutableListOf<Int>()
    repeat(6) {
      val idx = chainDeleteStep(boxes)
      if (idx != -1) order.add(idx)
    }
    // harus hapus dari 5 -> 0 berurutan, bukan sekaligus
    assertEquals(listOf(5,4,3,2,1,0), order)
    assertTrue(boxes.all { it.isEmpty() })
  }

  @Test fun chainDelete_tidakHapusSekaligus() {
    val boxes = mutableListOf("1","2","3","4","5","6")
    val first = chainDeleteStep(boxes)
    // step pertama hanya hapus 1 kotak (index 5), bukan 6 sekaligus
    assertEquals(5, first)
    assertEquals(1, boxes.count { it.isEmpty() })
    assertEquals(5, boxes.count { it.isNotEmpty() })
  }

  @Test fun chainDelete_berhentiSaatSudahKosong() {
    val boxes = mutableListOf("","","","","","")
    val idx = chainDeleteStep(boxes)
    assertEquals(-1, idx)
  }

  @Test fun chainDelete_parsialTerisi() {
    val boxes = mutableListOf("1","2","3","","","")
    val o1 = chainDeleteStep(boxes); assertEquals(2, o1)
    val o2 = chainDeleteStep(boxes); assertEquals(1, o2)
    val o3 = chainDeleteStep(boxes); assertEquals(0, o3)
    val o4 = chainDeleteStep(boxes); assertEquals(-1, o4)
  }

  @Test fun confirmDialog_reusableExists() {
    // Pastikan component reusable ada dan punya helper showGantiNomor
    val clazz = Class.forName("com.upme.messenger.presentation.common.ConfirmDialog")
    assertNotNull(clazz)
    val companion = clazz.declaredClasses.find { it.simpleName == "Companion" }
    // companion harus punya showGantiNomor (WA-style)
    val method = clazz.methods.find { it.name == "showGantiNomor" } ?: companion?.methods?.find { it.name == "showGantiNomor" }
    assertNotNull("ConfirmDialog.showGantiNomor harus ada sebagai reusable API", method)
  }

  @Test fun otpFragment_tidakPakaiAlertDialogUntukGantiNomor() {
    // OtpFragment harus pakai ConfirmDialog, bukan AlertDialog langsung untuk ganti nomor
    val candidates = listOf(
      "src/main/java/com/upme/messenger/presentation/auth/OtpFragment.kt",
      "app/src/main/java/com/upme/messenger/presentation/auth/OtpFragment.kt",
      "/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/presentation/auth/OtpFragment.kt"
    )
    val file = candidates.map { java.io.File(it) }.firstOrNull { it.exists() } ?: java.io.File(candidates[0])
    val src = file.readText()
    // ada import ConfirmDialog
    assertTrue("OtpFragment harus import ConfirmDialog reusable", src.contains("import com.upme.messenger.presentation.common.ConfirmDialog"))
    // pemanggilan showGantiNomor harus ada
    assertTrue("OtpFragment harus pakai ConfirmDialog.showGantiNomor", src.contains("ConfirmDialog.showGantiNomor"))
    // tidak boleh ada AlertDialog.Builder untuk ganti nomor lagi (hanya untuk loading/2FA)
    val gantiNomorAlerts = Regex("""AlertDialog\.Builder.*Ganti nomor""").findAll(src).count()
    assertEquals("Ganti nomor tidak boleh pakai AlertDialog bawaan", 0, gantiNomorAlerts)
  }

  @Test fun appToast_reusableExistsDanTidakPakaiBawaan() {
    // AppToast harus ada dan dipakai, bukan android.widget.Toast — cek file, bukan class load (butuh Android runtime)
    val candidates = listOf(
      java.io.File("src/main/java/com/upme/messenger/presentation/common/AppToast.kt"),
      java.io.File("app/src/main/java/com/upme/messenger/presentation/common/AppToast.kt"),
      java.io.File("/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/presentation/common/AppToast.kt")
    )
    val f = candidates.firstOrNull { it.exists() } ?: candidates.last()
    assertTrue("AppToast.kt harus ada", f.exists())
    val src = f.readText()
    assertTrue(src.contains("object AppToast"))
    assertTrue(src.contains("fun show("))
    // semua presentation file tidak boleh lagi pakai Toast.makeText
    val presDirCandidates = listOf(
      java.io.File("src/main/java/com/upme/messenger/presentation"),
      java.io.File("app/src/main/java/com/upme/messenger/presentation"),
      java.io.File("/home/demo/upchat/mobile/app/src/main/java/com/upme/messenger/presentation")
    )
    val presDir = presDirCandidates.firstOrNull { it.exists() } ?: presDirCandidates.last()
    val offenders = presDir.walkTopDown().filter { it.extension == "kt" }.map { f ->
      val c = f.readText()
      if (c.contains("Toast.makeText") && !f.name.contains("AppToast")) f.name to c.count { it == 'T' } else null
    }.filterNotNull().toList()
    // tidak ada file presentation yang masih pakai Toast.makeText (kecuali AppToast.kt sendiri)
    assertTrue("Tidak boleh ada Toast.makeText bawaan di presentation (found: $offenders)", offenders.isEmpty())
  }

  @Test fun manropeFont_terpasangDanDipakai() {
    // font Manrope harus terpasang
    val fontCandidates = listOf(
      java.io.File("src/main/res/font/manrope.xml"),
      java.io.File("app/src/main/res/font/manrope.xml"),
      java.io.File("/home/demo/upchat/mobile/app/src/main/res/font/manrope.xml")
    )
    val fontFile = fontCandidates.firstOrNull { it.exists() } ?: fontCandidates.last()
    assertTrue("font/manrope.xml harus ada", fontFile.exists())
    val fontXml = fontFile.readText()
    assertTrue(fontXml.contains("manrope_regular"))
    assertTrue(fontXml.contains("manrope_medium"))
    assertTrue(fontXml.contains("manrope_bold"))
    // themes harus pakai manrope
    val themeCandidates = listOf(
      java.io.File("src/main/res/values/themes.xml"),
      java.io.File("app/src/main/res/values/themes.xml"),
      java.io.File("/home/demo/upchat/mobile/app/src/main/res/values/themes.xml")
    )
    val themeFile = themeCandidates.firstOrNull { it.exists() } ?: themeCandidates.last()
    val theme = themeFile.readText()
    assertTrue("Theme harus pakai fontFamily manrope", theme.contains("@font/manrope"))
    // toast_custom harus pakai manrope
    val toastCandidates = listOf(
      java.io.File("src/main/res/layout/toast_custom.xml"),
      java.io.File("app/src/main/res/layout/toast_custom.xml"),
      java.io.File("/home/demo/upchat/mobile/app/src/main/res/layout/toast_custom.xml")
    )
    val toastFile = toastCandidates.firstOrNull { it.exists() } ?: toastCandidates.last()
    assertTrue("toast_custom.xml harus ada", toastFile.exists())
    assertTrue(toastFile.readText().contains("@font/manrope"))
  }
}
