package ru.netology.nmedia.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.Attachment
import ru.netology.nmedia.dto.Media
import ru.netology.nmedia.dto.MediaUpload
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.toEntity
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.error.ApiError
import ru.netology.nmedia.error.AppError
import ru.netology.nmedia.error.NetworkError
import ru.netology.nmedia.error.UnknownError
import ru.netology.nmedia.util.NetworkMonitor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PostRepositoryImpl @Inject constructor(
    private val postDao: PostDao,
    private val apiService: ApiService,
    private val auth: AppAuth,
    private val networkMonitor: NetworkMonitor,
) : PostRepository {

    // Вспомогательная функция для проверки сети
    private suspend fun isOnline(): Boolean =
        networkMonitor.isOnline.firstOrNull() ?: false

    // Источник данных в зависимости от сети
    override val data: Flow<PagingData<Post>> = networkMonitor.isOnline
        .flatMapLatest { isOnline ->
            if (isOnline) {
                // Если есть сеть — данные с сервера
                auth.authStateFlow.flatMapLatest {
                    Pager(
                        config = PagingConfig(pageSize = 5, enablePlaceholders = false),
                        pagingSourceFactory = { PostPagingSource(apiService) },
                    ).flow
                }
            } else {
                // Нет сети — данные из БД
                Pager(
                    config = PagingConfig(pageSize = 5, enablePlaceholders = false),
                    pagingSourceFactory = { DbPagingSource(postDao) },
                ).flow
            }
        }
//    override val data: Flow<PagingData<Post>> = Pager(
//        config = PagingConfig(pageSize = 5, enablePlaceholders = false),
//        pagingSourceFactory = { PostPagingSource(apiService) },
//    ).flow

    override suspend fun getAll() {
        if (!isOnline()) return

        try {
            val response = apiService.getAll()
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val body =
                response.body() ?: throw ApiError(response.code(), response.message())
            postDao.insert(body.toEntity())
        } catch (e: IOException) {
            throw NetworkError
        } catch (e: Exception) {
            throw UnknownError
        }
    }

    override fun getNewerCount(id: Long): Flow<Int> = flow {
        while (true) {
            delay(120_000L)
            // Проверка сети
            if (!isOnline()) continue

            val response = apiService.getNewer(id)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val body =
                response.body() ?: throw ApiError(response.code(), response.message())
            postDao.insert(body.toEntity())
            emit(body.size)
        }
    }
        .catch { e -> throw AppError.from(e) }
        .flowOn(Dispatchers.Default)

    override suspend fun save(post: Post, upload: MediaUpload?) {
        try {
            // Сначала сохраняем в БД (оптимистичное обновление)
            val postWithAttachment = upload?.let {
                val media = upload(it)
                post.copy(attachment = Attachment(media.id, AttachmentType.IMAGE))
            } ?: post

            val entity = if (postWithAttachment.id == 0L) {
                val newId = (postDao.getMaxId() ?: 0) + 1
                PostEntity.fromDto(postWithAttachment.copy(id = newId))
            } else {
                PostEntity.fromDto(postWithAttachment)
            }
            postDao.insert(entity)

            // Если есть сеть — отправляем на сервер
            if (isOnline()) {
                val response = apiService.save(postWithAttachment)
                if (!response.isSuccessful) {
                    throw ApiError(response.code(), response.message())
                }
                val body = response.body() ?: throw ApiError(response.code(), response.message())
                postDao.insert(PostEntity.fromDto(body))
            }
        } catch (e: IOException) {
            // При отсутствии сети пост уже сохранён в БД
            if (isOnline()) {
                throw NetworkError
            }
        } catch (e: Exception) {
            throw UnknownError
        }
    }

    override suspend fun removeById(id: Long) {
        // Сначала удаляем из БД
        postDao.removeById(id)

        // Если есть сеть — удаляем на сервере
        if (isOnline()) {
            try {
                val response = apiService.removeById(id)
                if (!response.isSuccessful) {
                    // Если сервер вернул ошибку — восстанавливаем запись в БД
                    throw ApiError(response.code(), response.message())
                }
            } catch (e: Exception) {
                // Восстанавливаем удалённую запись
                sync()
                throw e
            }
        }
    }

    override suspend fun likeById(id: Long) {
        val post = postDao.getById(id) ?: return
        val updatedPost = post.copy(
            likedByMe = !post.likedByMe,
            likes = if (post.likedByMe) post.likes - 1 else post.likes + 1
        )

        // Сначала обновляем в БД
        postDao.insert(updatedPost)

        // Если есть сеть — отправляем на сервер
        if (isOnline()) {
            try {
                val response = if (updatedPost.likedByMe) {
                    apiService.likeById(id)
                } else {
                    apiService.dislikeById(id)
                }
                if (!response.isSuccessful) {
                    throw ApiError(response.code(), response.message())
                }
                val body = response.body() ?: throw ApiError(response.code(), response.message())
                postDao.insert(PostEntity.fromDto(body))
            } catch (e: Exception) {
                // Восстанавливаем состояние лайка
                postDao.insert(post)
                throw e
            }
        }
    }

    override suspend fun upload(upload: MediaUpload): Media {
        if (!isOnline()) {
            throw NetworkError
        }

        try {
            val media = MultipartBody.Part.createFormData(
                "file", upload.file.name, upload.file.asRequestBody()
            )

            val response = apiService.upload(media)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            return response.body() ?: throw ApiError(response.code(), response.message())
        } catch (e: IOException) {
            throw NetworkError
        } catch (e: Exception) {
            throw UnknownError
        }
    }

    override suspend fun invalidateCache() {
        postDao.clearAll()
    }

    override suspend fun sync() {
        if (!isOnline()) return

        try {
            val response = apiService.getAll()
            if (response.isSuccessful) {
                response.body()?.let {
                    postDao.insert(it.toEntity())
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки синхронизации
        }
    }
}
