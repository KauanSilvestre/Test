package app.sentinela.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.sentinela.R
import app.sentinela.ui.home.HomeScreen
import app.sentinela.ui.profiles.ProfilesScreen
import app.sentinela.ui.records.RecordsScreen

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    PROFILES(R.string.tab_profiles, Icons.Filled.Settings),
    RECORDS(R.string.tab_records, Icons.Filled.List),
}

@Composable
fun SentinelaRoot(vm: SentinelaViewModel) {
    var tab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResourceSafe(entry.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(vm, content)
            Tab.PROFILES -> ProfilesScreen(vm, content)
            Tab.RECORDS -> RecordsScreen(vm, content)
        }
    }
}

@Composable
private fun stringResourceSafe(res: Int): String = androidx.compose.ui.res.stringResource(res)
