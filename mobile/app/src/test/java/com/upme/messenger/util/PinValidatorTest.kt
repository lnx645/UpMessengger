package com.upme.messenger.util
import org.junit.Assert.*
import org.junit.Test
class PinValidatorTest {
  @Test fun valid() {
    assertTrue(PinValidator.isValid("A1B2C3D4"))
    assertTrue(PinValidator.isValid("a1b2c3d4"))
    assertTrue(PinValidator.isValid("A1B2 C3D4"))
    assertFalse(PinValidator.isValid("G1B2C3D4"))
    assertFalse(PinValidator.isValid("A1B2C3D"))
    assertFalse(PinValidator.isValid("A1B2C3D44"))
    assertFalse(PinValidator.isValid(""))
  }
  @Test fun prettyNormalize() {
    assertEquals("A1B2 C3D4", PinValidator.pretty("a1b2c3d4"))
    assertEquals("A1B2C3D4", PinValidator.normalize("A1B2 C3D4"))
    assertEquals("A1B2 C3D4", PinValidator.pretty(" A1B2 C3D4 "))
  }
  @Test fun generate() {
    val p = PinValidator.generate()
    assertTrue(PinValidator.isValid(p))
    assertEquals(8, p.length)
  }
}
