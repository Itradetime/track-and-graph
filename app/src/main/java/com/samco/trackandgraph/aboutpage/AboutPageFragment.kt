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
package com.samco.trackandgraph.aboutpage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.ComposeView
import com.samco.trackandgraph.MainActivity
import com.samco.trackandgraph.NavButtonStyle
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.TnGComposeTheme
import timber.log.Timber

class AboutPageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val versionText = getVersionText()

        return ComposeView(requireContext()).apply {
            setContent {
                TnGComposeTheme {
                    AboutPageView(
                        versionText = versionText,
                        onRepoLinkClicked = { onRepoLinkClicked() }
                    )
                }
            }
        }
    }

    private fun onRepoLinkClicked() {
        val url = getString(R.string.github_link)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }

    private fun getVersionText(): String {
        return try {
            val pInfo = requireContext().packageManager
                .getPackageInfo(requireActivity().packageName, 0)
            "v${pInfo.versionName}"
        } catch (e: Exception) {
            Timber.d("Could not get package version name: ${e.message}")
            ""
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).setActionBarConfig(
            NavButtonStyle.MENU,
            getString(R.string.about)
        )
    }
}