package com.hack.smartcam

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.oned.UPCAWriter
import com.hack.smartcam.data.Repo
import com.hack.smartcam.data.Tiger
import com.hack.smartcam.food.BarcodeReader
import com.hack.smartcam.net.FoodFacts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The barcode feature end to end, minus the physical camera:
 * barcode image → ML Kit (same reader the live scanner uses) → Open Food Facts → food log (→ Tiger Data when configured).
 */
@RunWith(AndroidJUnit4::class)
class BarcodeFlowTest {
    private val nutella = "3017620422003"

    private fun render(code: String, format: BarcodeFormat = BarcodeFormat.EAN_13): Bitmap {
        val matrix = if (format == BarcodeFormat.UPC_A) UPCAWriter().encode(code, format, 640, 260) else EAN13Writer().encode(code, format, 640, 260)
        val pad = 60
        val bmp = Bitmap.createBitmap(matrix.width + pad * 2, matrix.height + pad * 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) if (matrix.get(x, y)) bmp.setPixel(x + pad, y + pad, Color.BLACK)
        return bmp
    }

    @Test
    fun checkDigitRejectsMisreads() {
        assertTrue(BarcodeReader.valid(nutella))
        assertFalse(BarcodeReader.valid("3017620422004"))
        assertFalse(BarcodeReader.valid("12345"))
        assertTrue(BarcodeReader.valid("036000291452")) // UPC-A
    }

    @Test
    fun readsEan13FromImage() = runBlocking {
        assertEquals(nutella, BarcodeReader.read(render(nutella)))
    }

    @Test
    fun readsUpcAFromImage() = runBlocking {
        val code = BarcodeReader.read(render("036000291452", BarcodeFormat.UPC_A))
        // ML Kit may report UPC-A as the 13-digit EAN form with a leading 0.
        assertTrue(code == "036000291452" || code == "0036000291452")
    }

    @Test
    fun blankImageHasNoBarcode() = runBlocking {
        val blank = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        assertNull(BarcodeReader.read(blank))
    }

    @Test
    fun scannedBarcodeGetsNutritionAndIsLogged() = runBlocking {
        val code = BarcodeReader.read(render(nutella))!!
        val food = FoodFacts.lookup(code)
        assertNotNull("Open Food Facts should know $code", food)
        food!!
        Log.i("BarcodeFlowTest", "Looked up: ${food.name} kcal=${food.kcal} sugar=${food.sugar} fat=${food.fat} grade=${food.grade}")
        assertTrue(food.name, food.name.contains("Nutella", ignoreCase = true))
        assertEquals("per 100 g", food.basis)
        assertTrue("kcal ${food.kcal}", food.kcal!! in 450.0..600.0)
        assertTrue("sugar ${food.sugar}", food.sugar!! > 40)
        assertTrue("fat ${food.fat}", food.fat!! > 20)
        assertEquals(code, food.barcode)
        assertTrue(FoodFacts.score(food) < 45) // Nutri-Score E → poor

        Repo.addFood(food)
        val saved = Repo.state.value.foods.first { it.id == food.id }
        assertEquals(food.kcal, saved.kcal)

        // Tiger Data round trip (what the website reads), when the database is configured.
        if (com.hack.smartcam.Keys.db != null) {
            Tiger.connect()
            Tiger.upsertFood("barcode-test", food)
            val remote = Tiger.fetchFoods("barcode-test")
            assertNotNull("Tiger fetch failed: ${Tiger.status.value}", remote)
            assertTrue(remote!!.any { it.id == food.id && it.kcal == food.kcal })
            Tiger.deleteUser("barcode-test")
        } else {
            Log.w("BarcodeFlowTest", "Tiger Data not configured: ${com.hack.smartcam.Keys.dbProblem}")
        }
        Repo.removeFood(food.id)
        assertFalse(Repo.state.value.foods.any { it.id == food.id })
    }

    @Test
    fun unknownBarcodeReturnsNull() = runBlocking {
        assertNull(FoodFacts.lookup("0000000000000"))
    }
}
