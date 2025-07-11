package eu.kanade.tachiyomi.extension.ja.rawxz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RawXZ : Madara(
    "RawXZ",
    "https://rawxjp.com",
    "ja",
    dateFormat = SimpleDateFormat("M月 d, yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
