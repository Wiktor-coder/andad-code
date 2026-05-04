package ru.netology.nmedia.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.BuildConfig
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.CardPostBinding
import ru.netology.nmedia.databinding.ItemSeparatorBinding
import ru.netology.nmedia.dto.FeedItem
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.view.loadCircleCrop
import java.text.SimpleDateFormat
import java.util.Locale

interface OnInteractionListener {
    fun onLike(post: Post) {}
    fun onEdit(post: Post) {}
    fun onRemove(post: Post) {}
    fun onShare(post: Post) {}
}

class FeedItemAdapter(
    private val onInteractionListener: OnInteractionListener,
) : PagingDataAdapter<FeedItem, RecyclerView.ViewHolder>(FeedItemDiffCallback()) {

    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_SEPARATOR = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FeedItem.PostItem -> TYPE_POST
            is FeedItem.SeparatorItem -> TYPE_SEPARATOR
            null -> TYPE_POST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_POST -> {
                val binding = CardPostBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                PostViewHolder(binding, onInteractionListener)
            }
            else -> {
                val binding = ItemSeparatorBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SeparatorViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PostViewHolder -> {
                val item = getItem(position) as? FeedItem.PostItem
                //Лог для отладки
                println("Binding post at position $position: ${item?.post?.content}")
                item?.let { holder.bind(it.post) }
            }
            is SeparatorViewHolder -> {
                val item = getItem(position) as? FeedItem.SeparatorItem
                //Лог для отладки
                println("Binding separator at position $position: ${item?.text}")
                item?.let { holder.bind(it) }
            }
        }
    }
}

class SeparatorViewHolder(
    private val binding: ItemSeparatorBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    fun bind(separator: FeedItem.SeparatorItem) {
        binding.separatorText.text = separator.text
        binding.separatorDate.text = dateFormat.format(separator.date)
    }
}

class PostViewHolder(
    private val binding: CardPostBinding,
    private val onInteractionListener: OnInteractionListener,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(post: Post) {
        binding.apply {
            author.text = post.author
            published.text = post.published.toString()
            content.text = post.content
            avatar.loadCircleCrop("${BuildConfig.BASE_URL}/avatars/${post.authorAvatar}")
            like.isChecked = post.likedByMe
            like.text = "${post.likes}"

            menu.visibility = if (post.ownedByMe) View.VISIBLE else View.INVISIBLE

            menu.setOnClickListener {
                PopupMenu(it.context, it).apply {
                    inflate(R.menu.options_post)
                    menu.setGroupVisible(R.id.owned, post.ownedByMe)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.remove -> {
                                onInteractionListener.onRemove(post)
                                true
                            }
                            R.id.edit -> {
                                onInteractionListener.onEdit(post)
                                true
                            }
                            else -> false
                        }
                    }
                }.show()
            }

            like.setOnClickListener {
                onInteractionListener.onLike(post)
            }

            share.setOnClickListener {
                onInteractionListener.onShare(post)
            }
        }
    }
}

class FeedItemDiffCallback : DiffUtil.ItemCallback<FeedItem>() {
    override fun areItemsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
        return when {
            oldItem is FeedItem.PostItem && newItem is FeedItem.PostItem ->
                oldItem.post.id == newItem.post.id
            oldItem is FeedItem.SeparatorItem && newItem is FeedItem.SeparatorItem ->
                oldItem.text == newItem.text
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
        return oldItem == newItem
    }
}