package ru.netology.nmedia.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dao.PostRemoteKeyDao
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.PostRemoteKeyEntity
import ru.netology.nmedia.entity.toEntity
import ru.netology.nmedia.error.ApiError

@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val service: ApiService,
    private val db: AppDb,
    private val postDao: PostDao,
    private val postRemoteKeyDao: PostRemoteKeyDao,
) : RemoteMediator<Int, PostEntity>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>
    ): MediatorResult {
        try {
            val response = when (loadType) {
//                LoadType.REFRESH -> service.getLatest(state.config.initialLoadSize)
                LoadType.REFRESH -> {
                    // REFRESH: получаем ID самого старого поста сверху
                    val oldestPostId = postDao.getOldestId()
                    if (oldestPostId != null) {
                        // Загружаем посты новее, чем самый старый пост
                        service.getNewer(oldestPostId)
                    } else {
                        // БД пуста — загружаем первую страницу
                        service.getLatest(state.config.initialLoadSize)
                    }
                }

                LoadType.PREPEND -> {
                    // PREPEND отключён — возвращаем успех без загрузки
                    return MediatorResult.Success(endOfPaginationReached = true)

//                    val id = postRemoteKeyDao.max() ?: return MediatorResult.Success(
//                        endOfPaginationReached = false
//                    )
//                    service.getAfter(id, state.config.pageSize)
                }

                LoadType.APPEND -> {
                    // Используем min для получения самого старого ID
                    val oldestId = postRemoteKeyDao.min()
                    if (oldestId != null) {
                        service.getBefore(oldestId, state.config.pageSize)
                    } else {
                        // Если нет ключа, пробуем загрузить страницу
                        service.getLatest(state.config.pageSize)
                    }
//                    val id = postRemoteKeyDao.min() ?: return MediatorResult.Success(
//                        endOfPaginationReached = false
//                    )
//                    service.getBefore(id, state.config.pageSize)
                }
            }

            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }
            val body = response.body() ?: throw ApiError(
                response.code(),
                response.message(),
            )

            if (body.isEmpty()) {
                return MediatorResult.Success(endOfPaginationReached = true)
            }

            db.withTransaction {
                when (loadType) {
                    LoadType.REFRESH -> {
                        // REFRESH: добавляем новые посты СВЕРХУ (не удаляем старые)
//                        val existingOldestId = postDao.getOldestId()
                        val existingNewestId = postDao.getNewestId()

                        // Фильтруем только новые посты (которых ещё нет в БД)
                        val newPosts = body.filter { newPost ->
                            existingNewestId == null || newPost.id > existingNewestId
                        }

                        if (newPosts.isNotEmpty()) {
                            postDao.insert(newPosts.toEntity())

                            // Обновляем ключи для APPEND
                            val newOldestId = postDao.getOldestId()
                            if (newOldestId != null) {
                                postRemoteKeyDao.insert(
                                    PostRemoteKeyEntity(
                                        type = PostRemoteKeyEntity.KeyType.BEFORE,
                                        id = newOldestId
                                    )
                                )
                            }
                        }
                    }
//                    LoadType.REFRESH -> {
//                        postRemoteKeyDao.removeAll()
//                        postRemoteKeyDao.insert(
//                            listOf(
//                                PostRemoteKeyEntity(
//                                    type = PostRemoteKeyEntity.KeyType.AFTER,
//                                    id = body.first().id,
//                                ),
//                                PostRemoteKeyEntity(
//                                    type = PostRemoteKeyEntity.KeyType.BEFORE,
//                                    id = body.last().id,
//                                ),
//                            )
//                        )
//                        postDao.removeAll()
//                    }
                    LoadType.PREPEND -> {
                        // Не используется
//                        postRemoteKeyDao.insert(
//                            PostRemoteKeyEntity(
//                                type = PostRemoteKeyEntity.KeyType.AFTER,
//                                id = body.first().id,
//                            )
//                        )
                    }

                    LoadType.APPEND -> {
                        postRemoteKeyDao.insert(
                            PostRemoteKeyEntity(
                                type = PostRemoteKeyEntity.KeyType.BEFORE,
                                id = body.last().id,
                            )
                        )
                        postDao.insert(body.toEntity())
                    }
                }

            }
            return MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }

            return MediatorResult.Error(e)
        }
    }
}