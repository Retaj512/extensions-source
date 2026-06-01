package eu.kanade.tachiyomi.extension.en.harimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Harimanga :
    Madara(
        "Harimanga",
        "https://www.harimanga.co.uk",
        "en",
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}