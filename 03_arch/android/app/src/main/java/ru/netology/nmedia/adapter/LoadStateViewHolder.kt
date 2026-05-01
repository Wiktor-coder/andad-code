package ru.netology.nmedia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nmedia.databinding.ItemLoadStateBinding

class PostLoadingStateAdapter(
    private val retry: () -> Unit,
) : LoadStateAdapter<LoadStateViewHolder>() {
    override fun onBindViewHolder(
        holder: LoadStateViewHolder,
        loadState: LoadState
    ) {
        holder.bind(loadState)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        loadState: LoadState
    ): LoadStateViewHolder {
        val binding = ItemLoadStateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LoadStateViewHolder(binding, retry)
    }
}

class LoadStateViewHolder(
    private val binding: ItemLoadStateBinding,
    private val retry: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(loadState: LoadState) {
        binding.apply {
            // Показываем ProgressBar только при загрузке
            progressBar.isVisible = loadState is LoadState.Loading
            progressBar.isIndeterminate = true

            // Показываем ошибку и кнопку retry при ошибке
            errorMessage.isVisible = loadState is LoadState.Error
            retryButton.isVisible = loadState is LoadState.Error

            if (loadState is LoadState.Error) {
                errorMessage.text = loadState.error.localizedMessage ?: "Ошибка загрузки"
                retryButton.setOnClickListener { retry.invoke() }
            }
        }
    }
}