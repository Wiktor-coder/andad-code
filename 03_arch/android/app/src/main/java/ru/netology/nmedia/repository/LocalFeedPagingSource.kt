package ru.netology.nmedia.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.FeedItem
import ru.netology.nmedia.util.SeparatorUtils

class LocalFeedPagingSource(
    private val postDao: PostDao,
) : PagingSource<Int, FeedItem>() {

    override fun getRefreshKey(state: PagingState<Int, FeedItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedItem> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val offset = page * pageSize

            val posts = postDao.getPosts(limit = pageSize, offset = offset)

            val items = mutableListOf<FeedItem>()
            var lastSeparatorType: String? = null

            posts.forEach { postEntity ->
                val post = postEntity.toDto()
                val separatorType = SeparatorUtils.getSeparatorType(post.published)

                if (lastSeparatorType != separatorType) {
                    lastSeparatorType = separatorType
                    items.add(
                        FeedItem.SeparatorItem(
                            text = separatorType,
                            date = java.util.Date(post.published)
                        )
                    )
                }
                items.add(FeedItem.PostItem(post))
            }

            LoadResult.Page(
                data = items,
                prevKey = if (page > 0) page - 1 else null,
                nextKey = if (posts.size == pageSize) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}