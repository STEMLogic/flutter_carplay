package com.oguzhnatly.flutter_carplay.models.search

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import com.oguzhnatly.flutter_carplay.CPSearchTemplate
import com.oguzhnatly.flutter_carplay.Debounce
import com.oguzhnatly.flutter_carplay.FCPChannelTypes
import com.oguzhnatly.flutter_carplay.FCPStreamHandlerPlugin
import com.oguzhnatly.flutter_carplay.FCPTemplate
import com.oguzhnatly.flutter_carplay.models.list.FCPListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** A custom template for performing searches on Android Auto.
 * @param obj A dictionary containing the properties of the search template.
 */
class FCPSearchTemplate(obj: Map<String, Any>) : FCPTemplate(), SearchTemplate.SearchCallback {
    /// The underlying CPSearchTemplate instance.
    private lateinit var _super: CPSearchTemplate

    /// A debounce object for optimizing search events.
    private var debounce = Debounce(CoroutineScope(Dispatchers.Main))

    /// The list of items to be displayed in the search template.
    private var resultItems: ItemList? = null

    init {
        val elementIdValue = obj["_elementId"] as? String
        assert(elementIdValue != null) {
            "Missing required key: _elementId"
        }

        elementId = elementIdValue!!
        onSearchTextChanged("")
    }

    /** Returns a `CPSearchTemplate` object representing the search template.
     * @return A `CPSearchTemplate` object.
     */
    override fun getTemplate(): CPSearchTemplate {
        val template = SearchTemplate.Builder(this).setActionStrip(
            ActionStrip.Builder().addAction(Action.Builder().setTitle("Cancel").setOnClickListener {
                AndroidAutoService.session?.pop()
                FCPStreamHandlerPlugin.sendEvent(
                    type = FCPChannelTypes.onSearchCancelled.name,
                    data = mapOf("elementId" to elementId)
                )
            }.build()).build()
        )
        resultItems?.let {
            template.setItemList(it)
        }
        _super = template.build()
        return _super
    }

    /** Handles the search text change event. */
    override fun onSearchTextChanged(searchText: String) {
        debounce.debounce(interval = 500L) {
            FCPStreamHandlerPlugin.sendEvent(
                type = FCPChannelTypes.onSearchTextUpdated.name,
                data = mapOf("elementId" to elementId, "query" to searchText)
            )
        }
    }

    /** Handles the search button press event. */
    override fun onSearchSubmitted(searchText: String) {
        FCPStreamHandlerPlugin.sendEvent(
            type = FCPChannelTypes.onSearchButtonPressed.name,
            data = mapOf("elementId" to elementId)
        )
    }

    /** Handles the search performed event. */
    fun searchPerformed(searchResults: List<FCPListItem>) {
        val builder = ItemList.Builder()
        searchResults.forEach {
            builder.addItem(it.getTemplate {
                FCPStreamHandlerPlugin.sendEvent(
                    type = FCPChannelTypes.onSearchResultSelected.name,
                    data = mapOf("elementId" to elementId, "itemElementId" to it.elementId)
                )
            })
        }
        resultItems = builder.build()
        onInvalidate()
    }
}
