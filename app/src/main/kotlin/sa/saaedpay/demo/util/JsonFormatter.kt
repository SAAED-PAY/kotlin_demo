package sa.saaedpay.demo.util

import com.google.gson.GsonBuilder

object JsonFormatter {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    fun format(obj: Any): String {
        return try {
            gson.toJson(obj)
                .lines()
                .joinToString("\n") { "  $it" }
        } catch (e: Exception) {
            "  [Could not serialize: ${e.message}]"
        }
    }
}
