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
                    val maxId = postRemoteKeyDao.max() //postDao.getOldestId()
                    if (maxId != null) {
                        // Загружаем посты новее, чем самый старый пост
                        service.getAfter(maxId, state.config.pageSize)
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
                    val id = postRemoteKeyDao.min() ?: return MediatorResult.Success(
                        endOfPaginationReached = false
                    )
                    service.getBefore(id, state.config.pageSize)
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
                        val wasDbEmpty = postRemoteKeyDao.isEmpty()

                        if (wasDbEmpty) {
                            // БД была пуста: сохраняем ВСЕ полученные посты
                            postDao.insert(body.toEntity())

                            // Сохраняем оба ключа: AFTER (самый новый) и BEFORE (самый старый)
                            postRemoteKeyDao.insert(
                                listOf(
                                    PostRemoteKeyEntity(
                                        type = PostRemoteKeyEntity.KeyType.AFTER,
                                        id = body.first().id
                                    ),
                                    PostRemoteKeyEntity(
                                        type = PostRemoteKeyEntity.KeyType.BEFORE,
                                        id = body.last().id
                                    )
                                )
                            )
                        } else {
                            // БД НЕ пуста: добавляем только НОВЫЕ посты (сверху)
                            val existingNewestId = postDao.getNewestId()

                            val newPosts = if (existingNewestId != null) {
                                body.filter { it.id > existingNewestId }
                            } else {
                                body
                            }

                            if (newPosts.isNotEmpty()) {
                                postDao.insert(newPosts.toEntity())

                                // Обновляем ONLY ключ AFTER (самый новый среди всех)
                                postRemoteKeyDao.insert(
                                    PostRemoteKeyEntity(
                                        type = PostRemoteKeyEntity.KeyType.AFTER,
                                        id = newPosts.first().id
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