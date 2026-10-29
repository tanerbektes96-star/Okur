package com.okur.reader.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.okur.reader.AppContainer
import com.okur.reader.data.EyePrefs
import com.okur.reader.eyecare.EyeComfortOverlay
import com.okur.reader.ui.eyecare.EyeCareScreen
import com.okur.reader.ui.library.LibraryScreen
import com.okur.reader.ui.library.LibraryViewModel
import com.okur.reader.ui.reader.ReaderScreen
import com.okur.reader.ui.settings.SettingsScreen
import com.okur.reader.ui.stats.StatsScreen
import com.okur.reader.ui.stats.StatsViewModel

private enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    LIBRARY("Kitaplık", Icons.Filled.LocalLibrary, Icons.Outlined.LocalLibrary),
    STATS("İstatistik", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    EYE("Göz Bakımı", Icons.Filled.RemoveRedEye, Icons.Outlined.RemoveRedEye),
    SETTINGS("Ayarlar", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun AppRoot(container: AppContainer, incoming: List<Uri>, onIncomingHandled: () -> Unit) {
    val nav = rememberNavController()
    val eye by container.settings.eyePrefs.collectAsStateWithLifecycle(initialValue = EyePrefs())
    val libraryVm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(container) } })

    // Dosya yöneticisinden "Birlikte aç" ile gelen kitaplar
    LaunchedEffect(incoming) {
        if (incoming.isNotEmpty()) {
            libraryVm.import(incoming, openAfter = incoming.size == 1)
            onIncomingHandled()
        }
    }
    LaunchedEffect(Unit) {
        libraryVm.openFlow.collect { id -> nav.navigate("reader/$id") { launchSingleTop = true } }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "main",
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable("main") {
                MainScaffold(container, libraryVm) { id -> nav.navigate("reader/$id") { launchSingleTop = true } }
            }
            composable("reader/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("bookId").orEmpty()
                ReaderScreen(bookId = id, container = container, onBack = { nav.popBackStack() })
            }
        }
        // Mavi ışık filtresi + ekstra karartma tüm ekranların üzerinde
        EyeComfortOverlay(eye)
    }
}

@Composable
private fun MainScaffold(container: AppContainer, libraryVm: LibraryViewModel, onOpenBook: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val useRail = LocalConfiguration.current.screenWidthDp >= 600
    val content: @Composable (Modifier) -> Unit = { modifier ->
        AnimatedContent(
            targetState = Tab.entries[tab],
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = modifier,
            label = "tab",
        ) { current ->
            when (current) {
                Tab.LIBRARY -> LibraryScreen(libraryVm, onOpenBook)
                Tab.STATS -> {
                    val vm: StatsViewModel = viewModel(factory = viewModelFactory { initializer { StatsViewModel(container) } })
                    StatsScreen(vm)
                }
                Tab.EYE -> EyeCareScreen(container)
                Tab.SETTINGS -> SettingsScreen(container)
            }
        }
    }

    if (useRail) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEachIndexed { i, t ->
                    NavigationRailItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(if (tab == i) t.selected else t.unselected, null) },
                        label = { Text(t.label) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            content(Modifier.weight(1f).fillMaxSize())
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(if (tab == i) t.selected else t.unselected, null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            content(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding))
        }
    }
}
