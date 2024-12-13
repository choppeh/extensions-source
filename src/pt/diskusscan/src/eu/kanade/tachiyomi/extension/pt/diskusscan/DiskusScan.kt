package eu.kanade.tachiyomi.extension.pt.diskusscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DiskusScan : MangaThemesia(
    "Diskus Scan",
    "https://diskusscan.online",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {

    // Changed their theme from Madara to MangaThemesia.
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        .set("Accept-Language", "pt-BR,en-US;q=0.7,en;q=0.3")
        .set("Alt-Used", baseUrl.substringAfterLast("/"))
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Sec-Fetch-User", "?1")

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + mangaUrlDirectory)
            .build()

        return GET(baseUrl + manga.url, newHeaders)
    }

    override val seriesAuthorSelector = ".infotable tr:contains(Autor) td:last-child"
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] > *:not([class^=disku])"
    override val seriesStatusSelector = ".infotable td:contains(Status) + td"

    // =========================== Chapters ================================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    // =========================== Filters ==================================

    override val statusOptions = arrayOf(
        "Todos" to "",
        "Ativo" to "ativo",
        "Finalizado" to "completa",
        "Hiato" to "hiato",
        "Dropada" to "dropada",
    )

    override fun String?.parseStatus() = when (orEmpty().trim().lowercase()) {
        "ativa" -> SManga.ONGOING
        "finalizada" -> SManga.COMPLETED
        "hiato" -> SManga.ON_HIATUS
        "dropada" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}
