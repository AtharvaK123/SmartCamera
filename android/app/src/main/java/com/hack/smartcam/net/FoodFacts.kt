package com.hack.smartcam.net

import com.hack.smartcam.data.Food
import com.hack.smartcam.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import kotlin.math.roundToInt

/** Nutrition for packaged foods from Open Food Facts (free, no key). */
object FoodFacts {
    private const val FIELDS = "product_name,brands,image_front_small_url,nutriments,nutriscore_grade,serving_size"

    suspend fun lookup(barcode: String): Food? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$FIELDS")
            .header("User-Agent", "SmartCam/0.3 (Android; hackathon prototype)")
            .build()
        Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful && res.code != 404) throw ApiException("Lookup failed (${res.code})")
            val root = Http.json.parseToJsonElement(res.body!!.string()).jsonObject
            if (root.str("status") != "1") return@withContext null
            val p = root["product"]?.jsonObject ?: return@withContext null
            val n = p["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
            fun g(k: String) = n.num(k)?.let { (it * 10).roundToInt() / 10.0 }
            val brand = p.str("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val name = p.str("product_name")?.trim()?.takeIf { it.isNotEmpty() }
            Food(
                id = Repo.newId("f"),
                t = System.currentTimeMillis(),
                name = when {
                    name == null -> brand ?: "Product $barcode"
                    brand == null || name.contains(brand, ignoreCase = true) -> name
                    else -> "$brand $name"
                },
                source = "barcode",
                barcode = barcode,
                image = p.str("image_front_small_url"),
                basis = "per 100 g",
                kcal = g("energy-kcal_100g"),
                protein = g("proteins_100g"),
                carbs = g("carbohydrates_100g"),
                sugar = g("sugars_100g"),
                fat = g("fat_100g"),
                satFat = g("saturated-fat_100g"),
                fiber = g("fiber_100g"),
                salt = g("salt_100g"),
                grade = p.str("nutriscore_grade")?.lowercase()?.takeIf { it.length == 1 && it[0] in 'a'..'e' },
            )
        }
    }

    private val GRADE = mapOf("a" to 92, "b" to 76, "c" to 58, "d" to 38, "e" to 20)

    /** 0–100: Nutri-Score when known, otherwise a simple nutrient heuristic. */
    fun score(f: Food): Int {
        f.grade?.let { g -> GRADE[g]?.let { return it } }
        fun v(x: Double?) = x ?: 0.0
        val s = 62 + minOf(v(f.protein), 25.0) * 0.8 + minOf(v(f.fiber), 10.0) * 1.6 -
            minOf(v(f.sugar), 40.0) * 0.8 - minOf(v(f.satFat), 15.0) * 1.5 - minOf(v(f.salt), 3.0) * 5
        return s.roundToInt().coerceIn(5, 98)
    }

    fun dayScore(items: List<Food>): Int? = if (items.isEmpty()) null else items.map { score(it) }.average().roundToInt()
}
