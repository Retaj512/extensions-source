package eu.kanade.tachiyomi.extension.en.harimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
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

    // Site serves content only with a real browser User-Agent
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")

    // All pages live under /home, not /manga/
    override val mangaSubString = "home"

    // Pagination is ?page=N not /page/N/
    override fun searchPage(page: Int) = if (page == 1) "" else "?page=$page"

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/home?m_orderby=views${if (page > 1) "&page=$page" else ""}", headers)

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/home?m_orderby=latest${if (page > 1) "&page=$page" else ""}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/home?s=${query.trim()}&post_type=wp-manga${if (page > 1) "&page=$page" else ""}", headers)

    // Chapter list via JSON API instead of broken AJAX
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/manga/").trimEnd('/')
        return GET("$baseUrl/api/comics/$slug/chapters?page=1&per_page=9999&order=desc", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments.dropLast(1).last()
        val data = json.decodeFromString<ChapterListResponse>(response.body.string())
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
        return data.data.chapters.map { ch ->
            SChapter.create().apply {
                name = ch.chapter_name
                url = "/manga/$slug/${ch.chapter_slug}"
                date_upload = runCatching { fmt.parse(ch.updated_at)?.time ?: 0L }.getOrDefault(0L)
                chapter_number = ch.chapter_num.toFloat()
            }
        }
    }

    @Serializable data class ChapterListResponse(val success: Boolean, val data: ChapterData)
    @Serializable data class ChapterData(val chapters: List<Chapter>)
    @Serializable data class Chapter(
        val chapter_num: Double,
        val chapter_name: String,
        val chapter_slug: String,
        val updated_at: String,
    )
}