/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.samco.trackandgraph.ui.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.samco.trackandgraph.R
import com.samco.trackandgraph.base.database.dto.DataPoint
import com.samco.trackandgraph.base.database.dto.Feature
import com.samco.trackandgraph.base.helpers.formatDayWeekDayMonthYearHourMinuteOneLine
import com.samco.trackandgraph.base.helpers.getDisplayValue

@Composable
fun InfoDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) = Dialog(
    onDismissRequest = onDismissRequest
) {
    Card(
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(state = rememberScrollState())
                .padding(dimensionResource(id = R.dimen.card_padding)),
            content = content
        )
    }
}


@Composable
fun DataPointInfoDialog(
    dataPoint: DataPoint,
    isDuration: Boolean,
    weekdayNames: List<String>,
    onDismissRequest: () -> Unit
) = InfoDialog(onDismissRequest) {
    Text(
        formatDayWeekDayMonthYearHourMinuteOneLine(
            LocalContext.current,
            weekdayNames,
            dataPoint.timestamp
        ),
        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
        fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
    )
    SpacingSmall()
    Text(dataPoint.getDisplayValue(isDuration))
    Text(dataPoint.note)
}

@Composable
fun DataPointValueAndDescription(
    modifier: Modifier,
    dataPoint: DataPoint,
    isDuration: Boolean
) = Column(modifier = modifier) {
    Text(
        text = dataPoint.getDisplayValue(isDuration),
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
        fontWeight = MaterialTheme.typography.labelLarge.fontWeight,
    )
    if (dataPoint.note.isNotEmpty()) {
        SpacingSmall()
        Text(
            text = dataPoint.note,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FeatureInfoDialog(
    feature: Feature,
    onDismissRequest: () -> Unit
) = InfoDialog(onDismissRequest) {
    Text(
        feature.name,
        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
        fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
    )
    SpacingSmall()
    Text(feature.description)
}

