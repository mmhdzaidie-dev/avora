package com.zai.avora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zai.avora.data.model.Track
import com.zai.avora.ui.components.AvoraMessageHost
import com.zai.avora.ui.components.OfflinePlaceholder
import com.zai.avora.ui.components.FullPlayerSheet
import com.zai.avora.ui.components.MiniPlayer
import com.zai.avora.ui.components.NotchedBarBodyHeight
import com.zai.avora.ui.components.NotchedBarLift
import com.zai.avora.ui.components.NotchedBottomBar
import com.zai.avora.ui.components.NotchedNavItem
import com.zai.avora.ui.navigation.Screen
import com.zai.avora.ui.screens.HomeScreen
import com.zai.avora.ui.screens.LibraryScreen
import com.zai.avora.ui.screens.SearchScreen
import com.zai.avora.ui.theme.DarkBorder
import com.zai.avora.ui.theme.DarkCard
import com.zai.avora.ui.theme.AvoraTheme
import com.zai.avora.ui.theme.MutedText
import com.zai.avora.ui.theme.PureBlack
import com.zai.avora.ui.theme.PureWhite
import com.zai.avora.viewmodel.MusicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by lazy {
        ViewModelProvider(
            application as AvoraApp,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MusicViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            AvoraTheme(themeMode = themeMode) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MusicViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoadingTrack by viewModel.isLoadingTrack.collectAsState()
    val isExpanded by viewModel.isPlayerExpanded.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val offlineMode by viewModel.offlineMode.collectAsState()

    BackHandler(enabled = isExpanded) {
        viewModel.expandPlayer(false)
    }

    val colors = com.zai.avora.ui.theme.AvoraAppTheme.colors

    val navInsetDensity = LocalDensity.current
    val navInset = with(navInsetDensity) { WindowInsets.navigationBars.getBottom(navInsetDensity).toDp() }
    val barTotalHeight = NotchedBarBodyHeight + NotchedBarLift + navInset

    val goToLibrary: () -> Unit = {
        navController.navigate(Screen.Library.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Scaffold(
            containerColor = colors.background,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateLeftPadding(LayoutDirection.Ltr),
                        end = innerPadding.calculateRightPadding(LayoutDirection.Ltr)
                    )
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize().padding(bottom = 24.dp)
                ) {
                    composable(Screen.Home.route) {
                        if (offlineMode) {
                            OfflinePlaceholder(
                                title = "Home",
                                onOpenLibrary = { goToLibrary() }
                            )
                        } else {
                            HomeScreen(viewModel = viewModel)
                        }
                    }
                    composable(Screen.Search.route) {
                        if (offlineMode) {
                            OfflinePlaceholder(
                                title = "Pencarian",
                                onOpenLibrary = { goToLibrary() }
                            )
                        } else {
                            SearchScreen(viewModel = viewModel)
                        }
                    }
                    composable(Screen.Library.route) {
                        LibraryScreen(viewModel = viewModel)
                    }
                }

                if (currentTrack != null && !isExpanded) {
                    MiniPlayerContainer(
                        viewModel = viewModel,
                        track = currentTrack!!,
                        isPlaying = isPlaying,
                        isLoading = isLoadingTrack,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = barTotalHeight)
                    )
                }
            }
        }

        NotchedBottomBar(
            items = Screen.bottomNavItems.map { NotchedNavItem(it.route, it.title, it.icon) },
            selectedRoute = currentRoute,
            onItemClick = { item ->
                navController.navigate(item.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        AnimatedVisibility(
            visible = isExpanded && currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            currentTrack?.let { track ->
                FullPlayerSheet(
                    track = track,
                    viewModel = viewModel,
                    isPlaying = isPlaying,
                    isLoading = isLoadingTrack,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onSkipNextClick = { viewModel.skipNext() },
                    onSkipPreviousClick = { viewModel.skipPrevious() },
                    onShuffleClick = { viewModel.toggleShuffle() },
                    onRepeatClick = { viewModel.toggleRepeat() },
                    onFavoriteClick = { viewModel.toggleFavorite(track) },
                    onDismiss = { viewModel.expandPlayer(false) }
                )
            }
        }

        AvoraMessageHost(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isExpanded) 40.dp else 150.dp)
        )
    }
}

@Composable
fun MiniPlayerContainer(
    viewModel: MusicViewModel,
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val currentPosMs by viewModel.playbackPosition.collectAsState()
    val durMs by viewModel.duration.collectAsState()
    val effDur = if (durMs > 0) durMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
    val progressFloat = (currentPosMs.toFloat() / effDur.toFloat()).coerceIn(0f, 1f)

    MiniPlayer(
        track = track,
        isPlaying = isPlaying,
        isLoading = isLoading,
        progress = progressFloat,
        onPlayPauseClick = { viewModel.togglePlayPause() },
        onSkipNextClick = { viewModel.skipNext() },
        onClick = { viewModel.expandPlayer(true) },
        modifier = modifier
    )
}
