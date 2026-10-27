package org.fossify.messages.adapters

import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemSearchResultBinding
import org.fossify.messages.extensions.RelativeDateTicker
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.forEachVisiblePosition
import org.fossify.messages.extensions.formatMessageDate
import org.fossify.messages.models.SearchResult

class SearchResultsAdapter(
    activity: SimpleActivity, var searchResults: ArrayList<SearchResult>, recyclerView: MyRecyclerView, highlightText: String, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var fontSize = activity.getTextSize()
    private var textToHighlight = highlightText
    private var relativeDateTicker: RelativeDateTicker? = null

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = searchResults.size

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = searchResults.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = searchResults.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        dateTicker().attach(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        relativeDateTicker?.detach(recyclerView)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun refreshDisplayedDates() {
        relativeDateTicker?.refreshNow()
    }

    private fun dateTicker(): RelativeDateTicker {
        return relativeDateTicker ?: RelativeDateTicker(
            isRelativeDateTime = { activity.config.useRelativeDateTime },
            refreshDates = ::refreshVisibleDates,
        ).also { relativeDateTicker = it }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchResult = searchResults[position]
        holder.bindView(searchResult, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            setupView(itemView, searchResult)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = searchResults.size

    fun updateItems(newItems: ArrayList<SearchResult>, highlightText: String = "") {
        if (newItems.hashCode() != searchResults.hashCode()) {
            searchResults = newItems.clone() as ArrayList<SearchResult>
            textToHighlight = highlightText
            notifyDataSetChanged()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun refreshVisibleDates(recyclerView: RecyclerView) {
        if (activity.isDestroyed || activity.isFinishing) {
            return
        }
        recyclerView.forEachVisiblePosition { child, position ->
            val searchResult = searchResults.getOrNull(position) ?: return@forEachVisiblePosition
            ItemSearchResultBinding.bind(child).searchResultDate.text =
                searchResult.dateMillis.formatMessageDate(
                    context = child.context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = true,
                )
        }
    }

    private fun setupView(view: View, searchResult: SearchResult) {
        ItemSearchResultBinding.bind(view).apply {
            searchResultTitle.apply {
                text = searchResult.title.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            searchResultSnippet.apply {
                text = searchResult.snippet.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            searchResultDate.apply {
                text = searchResult.dateMillis.formatMessageDate(
                    context = context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = true,
                )
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            SimpleContactsHelper(activity).loadContactImage(searchResult.photoUri, searchResultImage, searchResult.title)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = ItemSearchResultBinding.bind(holder.itemView)
            Glide.with(activity).clear(binding.searchResultImage)
        }
    }
}
