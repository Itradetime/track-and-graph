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
@file:OptIn(ExperimentalPagerApi::class, ExperimentalComposeUiApi::class)

package com.samco.trackandgraph.adddatapoint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.DialogTheme
import com.samco.trackandgraph.ui.compose.theming.tngColors
import com.samco.trackandgraph.ui.compose.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import org.threeten.bp.OffsetDateTime

@Composable
fun AddDataPointsDialog(viewModel: AddDataPointsViewModel, onDismissRequest: () -> Unit = {}) {
    val hidden by viewModel.hidden.observeAsState(true)

    //Call onDismissRequest when the dialog is hidden after being shown
    LaunchedEffect(true) {
        viewModel.dismissEvents.collect {
            println("DISMISSING")
            onDismissRequest()
        }
    }

    if (!hidden) {
        DialogTheme {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(dismissOnClickOutside = false)
            ) {
                AddDataPointsView(viewModel)
            }
        }
    }
}

@Composable
private fun AddDataPointsView(viewModel: AddDataPointsViewModel) = Surface {
    Column(
        modifier = Modifier
            .heightIn(max = 400.dp)
            .fillMaxWidth()
            .background(color = MaterialTheme.tngColors.surface)
            .padding(dimensionResource(id = R.dimen.card_padding))
    ) {
        val showTutorial by viewModel.showTutorial.observeAsState(false)
        if (showTutorial) AddDataPointsTutorial(viewModel.tutorialViewModel)
        else DataPointInputView(viewModel)
    }

    if (viewModel.showCancelConfirmDialog.observeAsState(false).value) {
        ConfirmCancelDialog(
            body = R.string.confirm_cancel_notes_will_be_lost,
            onDismissRequest = viewModel::onConfirmCancelDismissed,
            onConfirm = viewModel::onConfirmCancelConfirmed,
        )
    }
}

@Composable
private fun ColumnScope.DataPointInputView(viewModel: AddDataPointsViewModel) {
    HintHeader(viewModel)

    TrackerPager(Modifier.weight(1f, true), viewModel)

    BottomButtons(viewModel)
}

@Composable
private fun BottomButtons(viewModel: AddDataPointsViewModel) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SmallTextButton(
            stringRes = R.string.cancel,
            onClick = viewModel::onCancelClicked,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.tngColors.onSurface
            )
        )
        if (viewModel.skipButtonVisible.observeAsState(false).value) {
            SmallTextButton(
                stringRes = R.string.skip,
                onClick = {
                    focusManager.clearFocus()
                    viewModel.onSkipClicked()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.tngColors.onSurface
                )
            )
        }
        val addButtonRes =
            if (viewModel.updateMode.observeAsState(false).value) R.string.update
            else R.string.add
        SmallTextButton(stringRes = addButtonRes, onClick = {
            focusManager.clearFocus()
            viewModel.onAddClicked()
        })

    }
}

@Composable
private fun TrackerPager(modifier: Modifier, viewModel: AddDataPointsViewModel) {
    val count by viewModel.dataPointPages.observeAsState(0)
    val pagerState = rememberPagerState(initialPage = viewModel.currentPageIndex.value ?: 0)
    val focusManager = LocalFocusManager.current

    HorizontalPager(
        modifier = modifier,
        count = count,
        state = pagerState
    ) { page ->
        viewModel.getViewModel(page).observeAsState().value?.let {
            TrackerPage(
                viewModel = it,
                currentPage = page == pagerState.currentPage
            )
        }
    }

    //Synchronise page between view model and view:

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect {
            focusManager.clearFocus()
            viewModel.updateCurrentPage(it)
        }
    }

    val currentPage by viewModel.currentPageIndex.observeAsState(0)
    val scope = rememberCoroutineScope()

    if (currentPage != pagerState.currentPage) {
        LaunchedEffect(currentPage) {
            scope.launch {
                pagerState.animateScrollToPage(currentPage)
            }
        }
    }
}

@Composable
private fun TrackerPage(
    viewModel: AddDataPointViewModel,
    currentPage: Boolean
) = FadingScrollColumn(
    modifier = Modifier.padding(horizontal = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {

    val focusManager = LocalFocusManager.current
    val valueFocusRequester = remember { FocusRequester() }

    TrackerNameHeadline(name = viewModel.name.observeAsState("").value)

    SpacingSmall()

    val selectedDateTime by viewModel.timestamp.observeAsState(OffsetDateTime.now())

    DateTimeButtonRow(
        modifier = Modifier.fillMaxWidth(),
        selectedDateTime = selectedDateTime,
        onDateTimeSelected = viewModel::updateTimestamp
    )

    SpacingLarge()

    val suggestedValues by viewModel.suggestedValues.observeAsState(emptyList())
    val selectedSuggestedValue by viewModel.selectedSuggestedValue.observeAsState()

    val focusScope = rememberCoroutineScope()

    if (suggestedValues.isNotEmpty()) {
        SuggestedValues(
            suggestedValues,
            selectedSuggestedValue,
            viewModel::onSuggestedValueSelected,
            onSuggestedValueLongPress = {
                viewModel.onSuggestedValueLongPress(it)
                focusScope.launch {
                    delay(100)
                    valueFocusRequester.requestFocus()
                }
            }
        )
        SpacingLarge()
    }

    when (viewModel) {
        is AddDataPointViewModel.NumericalDataPointViewModel -> {
            LabeledRow(label = stringResource(id = R.string.value_colon)) {
                ValueInputTextField(
                    textFieldValue = viewModel.value,
                    onValueChange = viewModel::setValueText,
                    focusManager = focusManager,
                    focusRequester = valueFocusRequester
                )
            }
        }
        is AddDataPointViewModel.DurationDataPointViewModel -> {
            LabeledRow(label = stringResource(id = R.string.value_colon)) {
                DurationInput(
                    viewModel = viewModel,
                    focusManager = focusManager,
                    nextFocusDirection = FocusDirection.Down,
                    focusRequester = valueFocusRequester
                )
            }
        }
        else -> {}
    }

    LaunchedEffect(currentPage) {
        delay(50)
        val hasSuggestedValues = suggestedValues.any { it.value != null }
        if (currentPage && !hasSuggestedValues) valueFocusRequester.requestFocus()
    }

    SpacingSmall()

    LabeledRow(label = stringResource(id = R.string.label_colon)) {
        LabelInputTextField(
            textFieldValue = viewModel.label,
            onValueChange = viewModel::updateLabel,
            focusManager = focusManager
        )
    }

    SpacingSmall()

    NoteInput(viewModel)
}

@Composable
private fun SuggestedValues(
    list: List<SuggestedValueViewData>,
    selectedItem: SuggestedValueViewData?,
    onSuggestedValueSelected: (SuggestedValueViewData) -> Unit,
    onSuggestedValueLongPress: (SuggestedValueViewData) -> Unit
) {
    val focusManager = LocalFocusManager.current
    FadingLazyRow(
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(id = R.dimen.dialog_input_spacing),
            Alignment.CenterHorizontally
        )
    ) {
        items(count = list.size, itemContent = { index ->
            val suggestedValue = list[index]

            //We should not be passed values with null for everything
            val text: String =
                if (suggestedValue.label.isNullOrBlank() && suggestedValue.valueStr != null) suggestedValue.valueStr
                else if (suggestedValue.valueStr.isNullOrBlank() && suggestedValue.label != null) suggestedValue.label
                else "${suggestedValue.valueStr} : ${suggestedValue.label}"

            TextChip(
                text = text,
                isSelected = suggestedValue == selectedItem,
                onClick = {
                    focusManager.clearFocus()
                    onSuggestedValueSelected(suggestedValue)
                },
                onLongPress = {
                    onSuggestedValueLongPress(suggestedValue)
                }
            )
        })
    }
}

@Composable
private fun NoteInput(viewModel: AddDataPointViewModel) =
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val focusRequester = remember { FocusRequester() }
        val coroutineScope = rememberCoroutineScope()

        var showNoteBox by rememberSaveable { mutableStateOf(false) }

        if (viewModel.note.text.isNotEmpty() || showNoteBox) {
            FullWidthTextField(
                modifier = Modifier.heightIn(max = 200.dp),
                textFieldValue = viewModel.note,
                onValueChange = { viewModel.updateNote(it) },
                focusRequester = focusRequester,
                label = stringResource(id = R.string.note_input_hint),
                singleLine = false
            )
        } else {
            AddANoteButton {
                showNoteBox = true
                coroutineScope.launch {
                    delay(100)
                    focusRequester.requestFocus()
                }
            }
        }

    }

@Composable
private fun HintHeader(viewModel: AddDataPointsViewModel) =
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = viewModel.indexText.observeAsState("").value,
            fontSize = MaterialTheme.typography.body1.fontSize,
            fontWeight = MaterialTheme.typography.body1.fontWeight,
        )
        //Faq vecotor icon as a button
        IconButton(onClick = { viewModel.onTutorialButtonPressed() }) {
            Icon(
                painter = painterResource(id = R.drawable.faq_icon),
                contentDescription = stringResource(id = R.string.help),
                tint = MaterialTheme.colors.onSurface
            )
        }
    }

