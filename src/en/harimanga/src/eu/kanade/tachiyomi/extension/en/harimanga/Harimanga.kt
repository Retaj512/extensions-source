package eu.kanade.tachiyomi.extension.en.harimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Harimanga : Madara(
    "Harimanga",
    "https://www.harimanga.co.uk",
    "en",
) {
    private val json: Json by injectLazy()

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    // The site requires a browser User-Agent or returns empty/404
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")

    // All listing/search pages are under /home not /manga/
    override val mangaSubString = "home"

    // Pagination uses ?page=N not /page/N/
    override fun searchPage(page: Int): String = if (page == 1) "" else "?page=$page"

    // Popular manga
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/home?m_orderby=views${if (page > 1) "&page=$page" else ""}", headers)

    // Latest updates
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/home?m_orderby=latest${if (page > 1) "&page=$page" else ""}", headers)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/home?s=${query.trim()}&post_type=wp-manga${if (page > 1) "&page=$page" else ""}", headers)

    // No next-page nav element found, use item count to determine hasNextPage
    override fun popularMangaNextPageSelector() = "div.page-item-detail"
    override fun latestUpdatesNextPageSelector() = "div.page-item-detail"
    override fun searchMangaNextPageSelector() = "div.page-item-detail"

    // Disable the old AJAX chapter endpoint — use our JSON API override below
    override val useNewChapterEndpoint = true

    // Override chapter list to use the custom JSON API
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/manga/").trimEnd('/')
        return GET(
            "$baseUrl/api/comics/$slug/chapters?page=1&per_page=9999&order=desc",
            headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments
            .dropLast(1).last() // get slug from URL
        val body = response.body.string()
        val data = json.decodeFromString<ChapterListResponse>(body)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
        return data.data.chapters.map { chapter ->
            SChapter.create().apply {
                name = chapter.chapter_name
                url = "/manga/$slug/${chapter.chapter_slug}"
                date_upload = runCatching {
                    dateFormat.parse(chapter.updated_at)?.time ?: 0L
                }.getOrDefault(0L)
                chapter_number = chapter.chapter_num.toFloat()
            }
        }
    }

    // Chapter images — standard Madara HTML parsing works fine
    override fun pageListParse(document: Document) =
        document.select("div.page-break img.wp-manga-chapter-img")
            .mapIndexed { index, element ->
                eu.kanade.tachiyomi.source.model.Page(
                    index,
                    "",
                    (element.attr("abs:src").ifEmpty { element.attr("abs:data-src") }),
                )
            }

    @Serializable
    data class ChapterListResponse(val success: Boolean, val data: ChapterData)

    @Serializable
    data class ChapterData(
        val chapters: List<Chapter>,
        val total: Int,
        val current_page: Int,
        val per_page: Int,
        val last_page: Int,
    )

    @Serializable
    data class Chapter(
        val comic_id: Int,
        val chapter_id: Int,
        val chapter_num: Double,
        val chapter_name: String,
        val chapter_slug: String,
        val updated_at: String,
        val view: Int,
    )
}