package ru.netology.nmedia.adapter

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.R
import ru.netology.nmedia.databinding.CardPostBinding
import ru.netology.nmedia.dto.Post

interface OnInteractionListener {
    fun onLike(post: Post) {}
    fun onEdit(post: Post) {}
    fun onRemove(post: Post) {}
}

class PostsAdapter(
    private val onInteractionListener: OnInteractionListener,
) : ListAdapter<Post, PostViewHolder>(PostDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = CardPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding, onInteractionListener)
    }

//    override fun onViewRecycled(holder: PostViewHolder) {
//        super.onViewRecycled(holder)
//        holder.resetLikeAnimation()
//    }

    override fun onBindViewHolder(
        holder: PostViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            payloads.forEach {
                if (it is Payload) {
                    holder.bind(it)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        holder.bind(post)
    }
}

class PostViewHolder(
    private val binding: CardPostBinding,
    private val onInteractionListener: OnInteractionListener,
    private var currentAnimator: Animator? = null,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(payload: Payload) {
//        resetLikeAnimation()
        Log.d(
            "PostsAdapter",
            "bind payload for post with liked=${payload.liked}, view=${binding.like}"
        )
        payload.liked?.also { liked ->
            binding.like.setImageResource(
                if (liked) R.drawable.ic_liked_24 else R.drawable.ic_like_24
            )
            if (liked) {
                ObjectAnimator.ofPropertyValuesHolder(
                    binding.like,
                    PropertyValuesHolder.ofFloat(
                        View.SCALE_X,
                        1.0F, 1.2F, 1.0F, 1.2F
                    ),
                    PropertyValuesHolder.ofFloat(
                        View.SCALE_Y,
                        1.0F, 1.2F, 1.0F, 1.2F
                    ),
                ).start()
                ObjectAnimator.ofFloat(
                    binding.like,
                    View.ROTATION,
                    0F, 410F
                ).start()
            } else {
                ObjectAnimator.ofFloat(
                    binding.like,
                    View.ROTATION,
                    0F, 360F
                ).start()
            }
        }

        payload.content?.let(binding.content::setText)
    }

    fun bind(post: Post) {
        resetLikeAnimation()
        Log.d(
            "PostsAdapter",
            "bind full for post ${post.id}, view=${binding.like}"
        )
        binding.apply {
            author.text = post.author
            published.text = post.published
            content.text = post.content
            like.setImageResource(
                if (post.likedByMe) R.drawable.ic_liked_24 else R.drawable.ic_like_24
            )

            menu.setOnClickListener {
                PopupMenu(it.context, it).apply {
                    inflate(R.menu.options_post)
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
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1F, 1.25F, 1F)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1F, 1.25F, 1F)
                val animator = ObjectAnimator.ofPropertyValuesHolder(binding.like, scaleX, scaleY).apply {
                    duration = 500
                    repeatCount = 100
                    interpolator = BounceInterpolator()
                }
                currentAnimator = animator
                animator.start()
                onInteractionListener.onLike(post)
            }

//            like.setOnClickListener {
//                onInteractionListener.onLike(post)
//            }
        }
    }

    fun resetLikeAnimation() {
        Log.d("PostsAdapter", "reset animation for view ${binding.like}")
        currentAnimator?.cancel()
        currentAnimator = null
        binding.like.scaleX = 1F
        binding.like.scaleY = 1F
    }
}

class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
    override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: Post, newItem: Post): Any =
        Payload(
            liked = newItem.likedByMe.takeIf { oldItem.likedByMe != it },
            content = newItem.content.takeIf { oldItem.content != it },
        )
}

data class Payload(
    val liked: Boolean? = null,
    val content: String? = null,
)