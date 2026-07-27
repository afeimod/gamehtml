package com.flashbox.app.data

/** Common view-model entry used by the shared history/favorites adapter. */
data class BaseEntry(
    val id: Long,
    val title: String,
    val url: String,
    val isLocal: Boolean,
    val engine: String,
    val time: Long,
    val kind: Kind
) {
    enum class Kind { HISTORY, FAVORITE }
}

fun HistoryEntity.toEntry() = BaseEntry(id, title, url, isLocal, engine, visitedAt, BaseEntry.Kind.HISTORY)
fun FavoriteEntity.toEntry() = BaseEntry(id, title, url, isLocal, engine, addedAt, BaseEntry.Kind.FAVORITE)
