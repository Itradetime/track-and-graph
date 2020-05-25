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
package com.samco.trackandgraph.graphsandstats

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.samco.trackandgraph.R
import com.samco.trackandgraph.database.*
import com.samco.trackandgraph.databinding.FragmentViewGraphStatBinding
import com.samco.trackandgraph.graphstatview.GraphStatView
import kotlinx.coroutines.*


class ViewGraphStatFragment : Fragment() {
    private var navController: NavController? = null
    private lateinit var viewModel: ViewGraphStatViewModel
    private lateinit var graphStatView: GraphStatView
    private lateinit var binding: FragmentViewGraphStatBinding
    private val args: ViewGraphStatFragmentArgs by navArgs()

    private var showHideNotesAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        this.navController = container?.findNavController()
        viewModel = ViewModelProviders.of(this).get(ViewGraphStatViewModel::class.java)
        viewModel.init(activity!!, args.graphStatId)
        binding = FragmentViewGraphStatBinding.inflate(inflater, container, false)
        graphStatView = binding.graphStatView
        graphStatView.addLineGraphPanAndZoom()
        listenToState()
        listenToBinding()
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun listenToBinding() {
        binding.showNotesButton.setOnTouchListener{ _, motionEvent ->
            if (motionEvent?.action == MotionEvent.ACTION_DOWN) {
                viewModel.showHideNotesClicked()
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }
    }

    private fun listenToState() {
        viewModel.state.observe(this, Observer {
            when (it) {
                ViewGraphStatViewModelState.INITIALIZING -> graphStatView.initLoading()
                ViewGraphStatViewModelState.WAITING -> initGraphStatViewFromViewModel()
                else -> run {}
            }
        })

        viewModel.showingNotes.observe(this, Observer { b -> onShowingNotesChanged(b) })
    }

    private fun onShowingNotesChanged(showNotes: Boolean) {
        val largeSize =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 1.4f
            else 10000f
        val toValue = if (showNotes) largeSize else 0f
        animateNotesRecycler(toValue)
        binding.notesPopupUpButton.visibility = if (showNotes) View.GONE else View.VISIBLE
        binding.notesPopupDownButton.visibility = if (showNotes) View.VISIBLE else View.GONE
    }

    private fun animateNotesRecycler(toValue: Float) {
        showHideNotesAnimator?.cancel()
        val currentWeight =
            (binding.notesRecyclerView.layoutParams as LinearLayout.LayoutParams).weight
        showHideNotesAnimator = ValueAnimator.ofFloat(currentWeight, toValue)
        showHideNotesAnimator!!.duration =
            resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        showHideNotesAnimator!!.interpolator = LinearInterpolator()
        showHideNotesAnimator!!.addUpdateListener { animation ->
            (binding.notesRecyclerView.layoutParams as LinearLayout.LayoutParams).weight =
                animation.animatedValue as Float
            binding.notesRecyclerView.requestLayout()
        }
        showHideNotesAnimator!!.start()
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar!!.hide()
    }

    override fun onStop() {
        super.onStop()
        (activity as AppCompatActivity).supportActionBar!!.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        graphStatView.dispose()
    }

    private fun initGraphStatViewFromViewModel() {
        val graphStat = viewModel.graphStatObject
        if (graphStat == null) graphStatView.initError(null, R.string.graph_stat_view_not_found)
        when (viewModel.graphStatInnerObject) {
            null -> graphStatView.initError(null, R.string.graph_stat_view_not_found)
            is LineGraph -> graphStatView.initFromLineGraph(
                graphStat!!,
                viewModel.graphStatInnerObject as LineGraph
            )
            is PieChart -> graphStatView.initFromPieChart(
                graphStat!!,
                viewModel.graphStatInnerObject as PieChart
            )
            is TimeSinceLastStat -> graphStatView.initTimeSinceStat(
                graphStat!!,
                viewModel.graphStatInnerObject as TimeSinceLastStat
            )
            is AverageTimeBetweenStat -> graphStatView.initAverageTimeBetweenStat(
                graphStat!!,
                viewModel.graphStatInnerObject as AverageTimeBetweenStat
            )
            else -> graphStatView.initError(null, R.string.graph_stat_validation_unknown)
        }
    }
}

enum class ViewGraphStatViewModelState { INITIALIZING, WAITING }
class ViewGraphStatViewModel : ViewModel() {
    var graphStatObject: GraphOrStat? = null
        private set
    var graphStatInnerObject: Any? = null
        private set

    val state: LiveData<ViewGraphStatViewModelState>
        get() {
            return _state
        }
    private val _state = MutableLiveData(ViewGraphStatViewModelState.INITIALIZING)

    val showingNotes: LiveData<Boolean>
        get() {
            return _showingNotes
        }
    private val _showingNotes = MutableLiveData(false)

    private val currJob = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + currJob)

    private var dataSource: TrackAndGraphDatabaseDao? = null

    fun init(activity: Activity, graphStatId: Long) {
        if (dataSource != null) return
        dataSource =
            TrackAndGraphDatabase.getInstance(activity.application).trackAndGraphDatabaseDao
        _state.value = ViewGraphStatViewModelState.INITIALIZING
        ioScope.launch {
            initFromGraphStatId(graphStatId)
            withContext(Dispatchers.Main) { _state.value = ViewGraphStatViewModelState.WAITING }
        }
    }

    private fun initFromGraphStatId(graphStatId: Long) {
        graphStatObject = dataSource!!.getGraphStatById(graphStatId)
        when (graphStatObject!!.type) {
            GraphStatType.LINE_GRAPH -> initLineGraph()
            GraphStatType.PIE_CHART -> initPieChart()
            GraphStatType.TIME_SINCE -> initTimeSince()
            GraphStatType.AVERAGE_TIME_BETWEEN -> initAverageTimeBetween()
        }
    }

    fun showHideNotesClicked() {
        _showingNotes.value = _showingNotes.value?.not()
    }

    private fun initLineGraph() {
        graphStatInnerObject = dataSource!!.getLineGraphByGraphStatId(graphStatObject!!.id)
    }

    private fun initPieChart() {
        graphStatInnerObject = dataSource!!.getPieChartByGraphStatId(graphStatObject!!.id)
    }

    private fun initTimeSince() {
        graphStatInnerObject = dataSource!!.getTimeSinceLastStatByGraphStatId(graphStatObject!!.id)
    }

    private fun initAverageTimeBetween() {
        graphStatInnerObject =
            dataSource!!.getAverageTimeBetweenStatByGraphStatId(graphStatObject!!.id)
    }

    override fun onCleared() {
        super.onCleared()
        ioScope.cancel()
    }
}
