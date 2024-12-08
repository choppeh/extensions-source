package eu.kanade.tachiyomi.extension.all.snowmtl

import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.extension.all.snowmtl.Snowmtl.Companion.PAGE_REGEX
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

@RequiresApi(Build.VERSION_CODES.O)
class TranslateInterceptor(
    private val source: Source,
    private val client: OkHttpClient,
) : Interceptor {

    private val json: Json by injectLazy()

    private val apiTranslateUrl = "https://libretranslate.com"

    override fun intercept(chain: Interceptor.Chain): Response {
        if (source.target == source.origin) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val url = request.url.toString()

        if (PAGE_REGEX.containsMatchIn(url).not()) {
            return chain.proceed(request)
        }

        val translation = request.url.fragment?.parseAs<List<Translation>>()
            ?: return chain.proceed(request)

        val translateHeaders = request.headers.newBuilder()
            .set("Origin", apiTranslateUrl)
            .build()

        val translated = translation
            .filter { it.text.isNotBlank() }
            .map { caption ->
                val translate = translateText(caption, translateHeaders)
                caption.copy(text = translate.text)
            }

        val newRequest = request.newBuilder()
            .url("${url.substringBeforeLast("#")}#${json.encodeToString(translated)}")
            .build()

        return chain.proceed(newRequest)
    }

    private fun translateText(caption: Translation, translateHeaders: Headers): TranslateDto {
        val body = FormBody.Builder()
            .add("q", caption.text)
            .add("source", source.origin)
            .add("target", source.target)
            .add("format", "text")
            .add("secret", apiSecret)
            .build()

        val response =
            client.newCall(POST("$apiTranslateUrl/translate", translateHeaders, body))
                .execute()

        val translate = response.parseAs<TranslateDto>()
        return translate
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    private val apiSecret: String by lazy {
        val document = client.newCall(GET(apiTranslateUrl))
            .execute().asJsoup()

        val scriptSrc = document.selectFirst("script[src*=app]")!!.absUrl("src")

        val script = client.newCall(GET(scriptSrc))
            .execute().asJsoup().html()

        val varBlock = API_SECRET_REGEX.find(script)?.groups?.get("secret")?.value
            ?: return@lazy "empty:@"

        try {
            val secretBase64 = QuickJs.create().use { it.evaluate(varBlock) as String }
            Base64.decode(secretBase64, Base64.DEFAULT).toString(Charsets.UTF_8).ifEmpty { "secret: secretBase64" }
        } catch (e: Exception) {
            ""
        }
    }

    @Serializable
    class TranslateDto(
        @SerialName("translatedText")
        val text: String,
    )

    companion object {
        val API_SECRET_REGEX = """=\s+(?<secret>\([^;]+)""".toRegex()
    }
}
