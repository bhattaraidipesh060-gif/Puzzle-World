package com.jeiu.puzzleworld

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for the glassmorphic game/room grid. Each card mirrors
 * the web preview: coloured emoji header, title, two-line description, a badge
 * chip and a "▶ Enter" pill button. Used for both the Games grid and the
 * Escape-Rooms grid (badge text/colour are supplied per item).
 */
class GameCardAdapter(
    initial: List<GameItem>,
    private val onClick: (GameItem) -> Unit
) : RecyclerView.Adapter<GameCardAdapter.Holder>() {

    data class GameItem(
        val id: String,
        val emoji: String,
        val title: String,
        val desc: String,
        val badgeText: String,
        val badgeColorRes: Int,
        val artRes: Int,
        val cat: String = "",
        val searchText: String = ""
    )

    private val items = initial.toMutableList()

    fun update(newItems: List<GameItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val art: TextView = view.findViewById(R.id.game_art)
        val title: TextView = view.findViewById(R.id.game_title)
        val desc: TextView = view.findViewById(R.id.game_desc)
        val badge: TextView = view.findViewById(R.id.game_badge)
        val enter: TextView = view.findViewById(R.id.game_enter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_game_card, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.art.text = item.emoji
        holder.art.setBackgroundResource(item.artRes)
        holder.title.text = item.title
        holder.desc.text = item.desc
        holder.badge.text = item.badgeText
        holder.badge.setTextColor(ContextCompat.getColor(holder.badge.context, item.badgeColorRes))
        holder.itemView.setOnClickListener { onClick(item) }

        // Subtle staggered entrance.
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 30f
        holder.itemView.animate().alpha(1f).translationY(0f)
            .setStartDelay(position * 55L).setDuration(300).start()
    }

    override fun getItemCount() = items.size
}
