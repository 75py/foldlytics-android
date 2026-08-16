package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.PostureCheckpointSource
import kotlinx.coroutines.flow.Flow

class PostureCheckpointRepository(
    private val dao: PostureCheckpointDao,
) {
    suspend fun save(checkpoint: PostureCheckpoint) {
        dao.insert(checkpoint.toEntity())
    }

    suspend fun load(beginMillis: Long, endMillis: Long): List<PostureCheckpoint> =
        dao.load(beginMillis, endMillis).map(PostureCheckpointEntity::toModel)

    suspend fun latest(source: PostureCheckpointSource): PostureCheckpoint? =
        dao.latest(source.name)?.toModel()

    fun observeRevision(): Flow<Long> = dao.observeRevision()
}
