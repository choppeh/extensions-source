package eu.kanade.tachiyomi.extension.en.spidyscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class SpidyScans : Madara(
    "Spidy Scans",
    "https://spidyscans.xyz",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
