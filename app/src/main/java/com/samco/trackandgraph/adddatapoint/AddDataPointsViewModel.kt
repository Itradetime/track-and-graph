/*
* This file is part of Track & Graph
*
* Track & Graph is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Track & Graph is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.samco.trackandgraph.adddatapoint

import androidx.lifecycle.*
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.DataType
import com.samco.trackandgraph.base.database.dto.Tracker
import com.samco.trackandgraph.base.helpers.PrefHelper
import com.samco.trackandgraph.base.helpers.doubleFormatter
import com.samco.trackandgraph.base.helpers.formatTimeDuration
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.ui.viewmodels.DurationInputViewModel
import com.samco.trackandgraph.ui.viewmodels.DurationInputViewModelImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.threeten.bp.OffsetDateTime
import javax.inject.Inject

data class SuggestedValueViewData(
    val value: Double?,
    val valueStr: String?,
    val label: String?
)

interface AddDataPointBaseViewModel {
    val name: LiveData<String>
    val timestamp: LiveData<OffsetDateTime>
    val label: LiveData<String>
    val note: LiveData<String>
    val suggestedValues: LiveData<List<SuggestedValueViewData>>
    val selectedSuggestedValue: LiveData<SuggestedValueViewData?>

    fun getTracker(): Tracker
    fun updateLabel(label: String)
    fun updateNote(note: String)
    fun updateTimestamp(timestamp: OffsetDateTime)
    fun onSuggestedValueSelected(suggestedValue: SuggestedValueViewData)
    fun onSuggestedValueLongPress(suggestedValue: SuggestedValueViewData)
}

sealed interface AddDataPointViewModel : AddDataPointBaseViewModel {

    interface NumericalDataPointViewModel : AddDataPointViewModel {
        val value: LiveData<String>
        fun setValue(value: String)
    }

    interface DurationDataPointViewModel : AddDataPointViewModel, DurationInputViewModel
}

interface AddDataPointsViewModel {

    val showTutorial: LiveData<Boolean>
    val updateMode: LiveData<Boolean>
    val indexText: LiveData<String>
    val skipButtonVisible: LiveData<Boolean>
    val dataPointPages: LiveData<Int>
    val currentPageIndex: LiveData<Int>
    val tutorialViewModel: AddDataPointTutorialViewModel

    fun getViewModel(pageIndex: Int): LiveData<AddDataPointViewModel>

    fun onTutorialButtonPressed()

    fun onCancelClicked()
    fun onSkipClicked()
    fun onAddClicked()
    fun updateCurrentPage(page: Int)
}

interface AddDataPointsNavigationViewModel : AddDataPointsViewModel {
    val dismiss: LiveData<Boolean>

    fun initFromArgs(
        trackerIds: List<Long>,
        dataPointTimestamp: OffsetDateTime?,
        customInitialValue: Double?
    )
}

@HiltViewModel
class AddDataPointsViewModelImpl @Inject constructor(
    private val dataInteractor: DataInteractor,
    private val suggestedValueHelper: SuggestedValueHelper,
    @IODispatcher private val io: CoroutineDispatcher,
    private val prefHelper: PrefHelper
) : ViewModel(), AddDataPointsNavigationViewModel {

    private data class Config(
        val tracker: Tracker,
        val timestamp: OffsetDateTime?,
        val label: String?,
        val value: Double?,
        val note: String?
    )

    private val configFlow = MutableStateFlow<List<Config>>(emptyList())
    private val indexFlow = MutableStateFlow(0)
    override val dismiss = MutableLiveData(false)
    private val tutorialButtonPresses = MutableSharedFlow<Unit>()


    override val tutorialViewModel = AddDataPointTutorialViewModelImpl()

    //Show the tutorial if the user has no data or if they have pressed the tutorial button
    override val showTutorial: LiveData<Boolean> = merge(
        tutorialButtonPresses.map { true }
            .onStart { emit(!prefHelper.getHideDataPointTutorial()) },
        tutorialViewModel.onTutorialComplete.map { false }
    )
        .shareIn(viewModelScope, SharingStarted.Eagerly, 1)
        .asLiveData(viewModelScope.coroutineContext)

    init {
        viewModelScope.launch {
            tutorialViewModel.onTutorialComplete.collect {
                prefHelper.setHideDataPointTutorial(true)
            }
        }
    }

    override fun onTutorialButtonPressed() {
        viewModelScope.launch { tutorialButtonPresses.emit(Unit) }
    }

    override val dataPointPages = configFlow
        .map { it.size }
        .distinctUntilChanged()
        .asLiveData(viewModelScope.coroutineContext)

    override val currentPageIndex = indexFlow
        .asLiveData(viewModelScope.coroutineContext)

    private val viewModels: SharedFlow<List<AddDataPointViewModel>> = configFlow
        .map { it.map { config -> getViewModel(config) } }
        .shareIn(viewModelScope, SharingStarted.Eagerly, 1)

    override fun getViewModel(pageIndex: Int) = viewModels
        .map { it.getOrNull(pageIndex) }
        .filterNotNull()
        .asLiveData(viewModelScope.coroutineContext)


    private fun getViewModel(config: Config): AddDataPointViewModel {
        return when (config.tracker.dataType) {
            DataType.DURATION -> DataPointDurationViewModel(config)
            DataType.CONTINUOUS -> getNumericalViewModel(config)
        }
    }

    private val now = OffsetDateTime.now()

    private abstract inner class AddDataPointBaseViewModelImpl(
        protected val config: Config
    ) : AddDataPointBaseViewModel {
        override val name = MutableLiveData(config.tracker.name)
        override val timestamp = MutableLiveData(config.timestamp ?: now)
        override val label = MutableLiveData(config.label ?: "")
        override val note = MutableLiveData(config.note ?: "")
        override val selectedSuggestedValue = MutableLiveData<SuggestedValueViewData?>(null)

        override val suggestedValues: LiveData<List<SuggestedValueViewData>> = suggestedValueHelper
            .getSuggestedValues(config.tracker)
            .map { list ->
                list.map {
                    SuggestedValueViewData(
                        it.value,
                        getValueString(it.value, config.tracker.dataType.isDuration()),
                        it.label
                    )
                }
            }
            .flowOn(io)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
            .asLiveData(viewModelScope.coroutineContext)

        private fun getValueString(value: Double?, isDuration: Boolean): String? = when {
            value == null -> null
            isDuration -> formatTimeDuration(value.toLong())
            else -> doubleFormatter.format(value)
        }

        override fun getTracker() = config.tracker

        override fun updateLabel(label: String) {
            this.label.value = label
            this.selectedSuggestedValue.value = null
        }

        override fun updateNote(note: String) {
            this.note.value = note
        }

        override fun updateTimestamp(timestamp: OffsetDateTime) {
            this.timestamp.value = timestamp
        }

        override fun onSuggestedValueLongPress(suggestedValue: SuggestedValueViewData) {
            suggestedValue.label?.let { this.label.value = it }
        }

        override fun onSuggestedValueSelected(suggestedValue: SuggestedValueViewData) {
            suggestedValue.label?.let { this.label.value = it }
            this.selectedSuggestedValue.value = suggestedValue
            onAddClicked()
        }
    }

    private fun getNumericalViewModel(config: Config) =
        object : AddDataPointViewModel.NumericalDataPointViewModel,
            AddDataPointBaseViewModelImpl(config) {

            private val lastInput = MutableStateFlow(config.value?.toString() ?: "")

            override val value = lastInput
                .asLiveData(viewModelScope.coroutineContext)

            override fun setValue(value: String) {
                this.lastInput.value = value
                this.selectedSuggestedValue.value = null
            }

            override fun onSuggestedValueSelected(suggestedValue: SuggestedValueViewData) {
                suggestedValue.value?.let { this.lastInput.value = it.toString() }
                super.onSuggestedValueSelected(suggestedValue)
            }

            override fun onSuggestedValueLongPress(suggestedValue: SuggestedValueViewData) {
                suggestedValue.value?.let { this.lastInput.value = it.toString() }
                super.onSuggestedValueLongPress(suggestedValue)
            }
        }

    private inner class DataPointDurationViewModel(
        config: Config,
        private val durationInputViewModel: DurationInputViewModel = DurationInputViewModelImpl()
    ) :
        DurationInputViewModel by durationInputViewModel,
        AddDataPointBaseViewModelImpl(config),
        AddDataPointViewModel.DurationDataPointViewModel {

        override fun setHours(value: String) {
            durationInputViewModel.setHours(value)
            this.selectedSuggestedValue.value = null
        }

        override fun setMinutes(value: String) {
            durationInputViewModel.setMinutes(value)
            this.selectedSuggestedValue.value = null
        }

        override fun setSeconds(value: String) {
            durationInputViewModel.setSeconds(value)
            this.selectedSuggestedValue.value = null
        }

        override fun onSuggestedValueSelected(suggestedValue: SuggestedValueViewData) {
            suggestedValue.value?.let { this.setDurationFromDouble(it) }
            super.onSuggestedValueSelected(suggestedValue)
        }

        override fun onSuggestedValueLongPress(suggestedValue: SuggestedValueViewData) {
            suggestedValue.value?.let { this.setDurationFromDouble(it) }
            super.onSuggestedValueLongPress(suggestedValue)
        }
    }

    override val updateMode: LiveData<Boolean> = configFlow.map {
        it.size == 1 && it[0].timestamp != null
    }.asLiveData(viewModelScope.coroutineContext)

    override val indexText = combine(indexFlow, configFlow) { index, configs ->
        if (configs.size == 1) ""
        else "${index + 1} / ${configs.size}"
    }.asLiveData(viewModelScope.coroutineContext)

    override val skipButtonVisible = combine(indexFlow, configFlow) { index, configs ->
        if (configs.size == 1) false
        else index < configs.size
    }.asLiveData(viewModelScope.coroutineContext)

    private var initialized = false

    override fun onCancelClicked() {
        dismiss.value = true
    }

    override fun onSkipClicked() = incrementPageIndex()

    override fun onAddClicked() {
        viewModelScope.launch {
            combine(indexFlow, viewModels) { index, viewModels -> viewModels.getOrNull(index) }
                .take(1)
                .filterNotNull()
                .collect { insertDataPoint(it) }
        }
    }

    private suspend fun insertDataPoint(viewModel: AddDataPointViewModel) {
        getDataPoint(viewModel)?.let {
            dataInteractor.insertDataPoint(it)
            incrementPageIndex()
        }
    }

    private fun getDataPoint(viewModel: AddDataPointViewModel): DataPoint? {
        val timestamp = viewModel.timestamp.value ?: return null
        return DataPoint(
            timestamp = timestamp,
            featureId = viewModel.getTracker().featureId,
            value = getDoubleValue(viewModel),
            label = viewModel.label.value ?: "",
            note = viewModel.note.value ?: ""
        )
    }

    private fun getDoubleValue(viewModel: AddDataPointViewModel) = when (viewModel) {
        is AddDataPointViewModel.NumericalDataPointViewModel ->
            viewModel.value.value?.toDoubleOrNull() ?: 1.0
        is AddDataPointViewModel.DurationDataPointViewModel ->
            viewModel.getDurationAsDouble()
    }

    override fun updateCurrentPage(page: Int) {
        setPageIndex(page)
    }

    private fun incrementPageIndex() {
        if (!setPageIndex(indexFlow.value + 1)) dismiss.value = true
    }

    private fun setPageIndex(index: Int): Boolean {
        return if (index >= 0 && index < configFlow.value.size) {
            indexFlow.value = index
            true
        } else false
    }

    override fun initFromArgs(
        trackerIds: List<Long>,
        dataPointTimestamp: OffsetDateTime?,
        customInitialValue: Double?
    ) {
        if (initialized) return
        initialized = true

        viewModelScope.launch(io) {

            val configs = trackerIds
                .mapNotNull { dataInteractor.getTrackerById(it) }
                .map { getConfig(it, dataPointTimestamp, customInitialValue) }

            if (configs.isEmpty()) dismiss.value = true
            else configFlow.emit(configs)
        }
    }

    private suspend fun getConfig(
        tracker: Tracker,
        dataPointTimestamp: OffsetDateTime?,
        customInitialValue: Double?
    ): Config {
        val dataPoint = dataPointTimestamp?.let { timestamp ->
            dataInteractor.getDataPointByTimestampAndTrackerSync(
                tracker.id,
                timestamp
            )
        }
        return Config(
            tracker,
            dataPointTimestamp,
            dataPoint?.label,
            customInitialValue ?: dataPoint?.value,
            dataPoint?.note
        )
    }

    override fun onCleared() {
        super.onCleared()
        tutorialViewModel.cancel()
    }
}