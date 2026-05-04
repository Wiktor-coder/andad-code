package ru.netology.nmedia.dto

import java.util.Date

sealed class FeedItem {
    data class PostItem(val post: Post) : FeedItem()
    data class SeparatorItem(val text: String, val date: Date) : FeedItem()
}