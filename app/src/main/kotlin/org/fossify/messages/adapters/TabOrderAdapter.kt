package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemTabOrderBinding
import org.fossify.messages.helpers.ConversationTab

data class TabOrderEntry(
    val tab: ConversationTab,
    val title: String,
    val hidden: Boolean,
)

class TabOrderAdapter(
    private val activity: SimpleActivity,
) : RecyclerView.Adapter<TabOrderAdapter.ViewHolder>() {
    private val tabs = mutableListOf<TabOrderEntry>()
    private var itemTouchHelper: ItemTouchHelper? = null

    fun setItemTouchHelper(itemTouchHelper: ItemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper
    }

    fun submit(entries: List<TabOrderEntry>) {
        tabs.clear()
        tabs.addAll(entries)
        notifyDataSetChanged()
    }

    fun orderedTabs(): List<ConversationTab> = tabs.map { it.tab }

    fun move(from: Int, to: Int) {
        if (from !in tabs.indices || to !in tabs.indices) {
            return
        }
        val entry = tabs.removeAt(from)
        tabs.add(to, entry)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTabOrderBinding.inflate(activity.layoutInflater, parent, false)
        return ViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = tabs[position]
        val textColor = activity.getProperTextColor()
        holder.binding.apply {
            tabOrderTitle.text = entry.title
            tabOrderTitle.setTextColor(textColor)
            tabOrderHidden.beVisibleIf(entry.hidden)
            tabOrderHidden.setTextColor(textColor.adjustAlpha(0.6f))
            tabOrderDragHandle.applyColorFilter(textColor)
            tabOrderDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(holder)
                }
                false
            }
        }
    }

    override fun getItemCount() = tabs.size

    class ViewHolder(val binding: ItemTabOrderBinding) : RecyclerView.ViewHolder(binding.root)
}
