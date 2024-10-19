package eu.kanade.tachiyomi.extension.en.kenscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import org.jsoup.nodes.Element

class KenScans : Keyoapp(
    "Ken Scans",
    "https://kenscans.com",
    "en",
) {

    override fun Element.getImageUrl(selector: String): String? {
        return selectFirst(selector)?.attr("style")?.let {
            URL_REGEX.find(it)?.groups?.get("url")?.value
        }
    }

    companion object {
        val URL_REGEX = """url\("?(?<url>[^(\)|")]+)""".toRegex()
    }
}
