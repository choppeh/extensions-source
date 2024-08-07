package eu.kanade.tachiyomi.extension.fr.mangascan

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class MangaScan : MMRCMS(
    "Manga-Scan",
    "https://mangascan-fr.net",
    "fr",
    supportsAdvancedSearch = false,
    detailsTitleSelector = "div.col-sm-12 h1",
) {
    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Accept", "image/avif,image/webp,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
