package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class ResetScans : ParsedHttpSource() {
    override val name = "Reset Scans"

    override val baseUrl = "https://reset-scans.xyz"

    override val lang = "en"

    override val supportsLatest = false

    // Moved from madara
    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        if (genresList.isEmpty()) {
            filterParse(response)
        }
        return super.popularMangaParse(response)
    }

    override fun popularMangaSelector() = "#card-real a"

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("img")!!) {
            title = attr("alt")
            thumbnail_url = absUrl("data-src")
        }
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()

    override fun latestUpdatesSelector() =
        throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() =
        throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state.filter { it.state }.let { list ->
                        if (list.isNotEmpty()) {
                            list.forEach { genre -> url.addQueryParameter("genre[]", genre.id) }
                        }
                    }
                }

                is StatusList -> url.addQueryParameter("status", filter.selected())
                is TypeList -> url.addQueryParameter("type", filter.selected())

                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.substringAfter(PREFIX_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/manga/$slug" })
                .map { MangasPage(listOf(it), false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h2.text-2xl")!!.text()
        thumbnail_url = document.selectFirst("div.relative img")?.absUrl("src")
        description = document.selectFirst(".profile-manga, #description + p")?.text()
        genre = document.select("a[href*=genre]").joinToString { it.text() }
        document.selectFirst("div.hidden > p span:contains(Status) + a span")
            ?.let { status = parseToStatus(it.text()) }

        author = document.selectFirst("div.hidden > p span:contains(Author) + span")?.text()
        artist = document.selectFirst("div.hidden > p span:contains(Artist) + span")?.text()

        setUrlWithoutDomain(document.location())
    }

    private fun parseToStatus(status: String): Int {
        return when (status.lowercase()) {
            "completed" -> SManga.COMPLETED
            "ongoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var page = 1
        val chapters = mutableListOf<SChapter>()
        val url = super.chapterListRequest(manga).url.newBuilder()
        do {
            url.addQueryParameter("page", "${page++}").build()
            val document = client.newCall(GET(url.build(), headers)).execute().asJsoup()
            chapters += document.select(chapterListSelector()).map(::chapterFromElement)
        } while (document.select(chapterListNextPageSelector()).isNotEmpty())

        return Observable.just(chapters)
    }

    private fun chapterListNextPageSelector() = ".pagination li:last-child:not([aria-disabled*=true])"

    override fun chapterListSelector() = "#chapters-list a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("span")!!.text()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // ============================== Pages ==============================

    override fun imageUrlParse(document: Document) = ""

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#chapter-container img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src"))
        }
    }

    // ============================== Filters ==============================

    private var genresList = emptyList<Tag>()
    private var statusList = emptyList<Tag>()
    private var typeList = emptyList<Tag>()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        if (genresList.isNotEmpty()) {
            filters += listOf(
                TypeList(displayName = "Type", vals = typeList),
                Filter.Separator(),
                StatusList(displayName = "Status", vals = statusList),
                Filter.Separator(),
                GenreList(title = "Genres", genres = genresList),
            )
        } else {
            filters += Filter.Header("Press 'Reset' to attempt to show the genres, status and types")
        }

        return FilterList(filters)
    }

    private fun parseGenres(document: Document): List<Tag> {
        return document.select("[name='genre[]']").map { element ->
            Tag(
                name = element.attr("id"),
                value = element.attr("value"),
            )
        }
    }

    private fun parseSelection(document: Document, selector: String): List<Tag> {
        return document.select(selector).map { element ->
            Tag(
                name = element.text(),
                value = element.attr("value"),
            )
        }
    }

    private fun filterParse(response: Response) {
        val document = Jsoup.parseBodyFragment(response.peekBody(Long.MAX_VALUE).string())
        genresList = parseGenres(document)
        statusList = parseSelection(document, "#status option")
        typeList = parseSelection(document, "#type option")
    }

    private data class Tag(val name: String, val value: String)
    private class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreList(title: String, genres: List<Tag>) : Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.value) })
    private open class SelectionList(displayName: String, private val vals: List<Tag>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), state) {
        fun selected() = vals[state].value
    }

    private class TypeList(displayName: String, vals: List<Tag>) : SelectionList(displayName, vals)
    private class StatusList(displayName: String, vals: List<Tag>) : SelectionList(displayName, vals)

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
