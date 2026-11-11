package com.snapzy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.snapzy.app.core.comingsoon.ComingSoonHost
import com.snapzy.app.core.comingsoon.rememberComingSoonController
import com.snapzy.app.core.designsystem.SnapzyColors
import com.snapzy.app.core.designsystem.SnapzyTheme
import com.snapzy.app.core.designsystem.SnapzyType
import com.snapzy.app.core.navigation.Routes
import com.snapzy.app.feature.collage.CollageScreen
import com.snapzy.app.feature.editor.photo.PhotoEditorScreen
import com.snapzy.app.feature.editor.video.VideoEditorScreen
import com.snapzy.app.feature.export.ExportScreen
import com.snapzy.app.core.editing.EditKind
import com.snapzy.app.core.media.EditorSessionStore
import com.snapzy.app.feature.home.HomeScreen
import com.snapzy.app.feature.onboarding.OnboardingScreen
import com.snapzy.app.feature.picker.MediaPickerScreen
import kotlinx.coroutines.delay

@Composable
fun SnapzyApp() {
    SnapzyTheme {
        val navController = rememberNavController()
        val comingSoonController = rememberComingSoonController()

        ComingSoonHost(controller = comingSoonController) {
            NavHost(navController = navController, startDestination = Routes.SPLASH) {
                composable(Routes.SPLASH) {
                    SplashScreen(onFinished = { navController.navigateReplacingStart(Routes.ONBOARDING) })
                }
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onFinish = { navController.navigateReplacingStart(Routes.HOME) })
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onCreateVideo = { navController.navigate(Routes.MEDIA_PICKER_VIDEO) },
                        onCreatePhoto = { navController.navigate(Routes.MEDIA_PICKER_PHOTO) },
                        onCreateCollage = { navController.navigate(Routes.MEDIA_PICKER_COLLAGE) },
                        onGatedFeature = { comingSoonController.show(it) },
                        onOpenDraft = { project ->
                            // A reopened draft goes straight back into the
                            // editor it was made in.
                            EditorSessionStore.draftProject = project
                            val route = if (project.kind == EditKind.VIDEO) {
                                Routes.VIDEO_EDITOR
                            } else {
                                Routes.PHOTO_EDITOR
                            }
                            navController.navigate(route)
                        },
                    )
                }
                composable(Routes.MEDIA_PICKER_VIDEO) {
                    MediaPickerScreen(
                        mediaKind = "video",
                        onBack = { navController.popBackStack() },
                        onMediaSelected = {
                            navController.navigate(Routes.VIDEO_EDITOR) {
                                popUpTo(Routes.MEDIA_PICKER_VIDEO) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.MEDIA_PICKER_PHOTO) {
                    MediaPickerScreen(
                        mediaKind = "photo",
                        onBack = { navController.popBackStack() },
                        onMediaSelected = {
                            navController.navigate(Routes.PHOTO_EDITOR) {
                                popUpTo(Routes.MEDIA_PICKER_PHOTO) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.MEDIA_PICKER_COLLAGE) {
                    MediaPickerScreen(
                        mediaKind = "collage",
                        onBack = { navController.popBackStack() },
                        onMediaSelected = {
                            navController.navigate(Routes.COLLAGE_BUILDER) {
                                popUpTo(Routes.MEDIA_PICKER_COLLAGE) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.VIDEO_EDITOR) {
                    VideoEditorScreen(
                        onBack = { navController.popBackStack() },
                        onExport = { navController.navigate(Routes.EXPORT) },
                    )
                }
                composable(Routes.PHOTO_EDITOR) {
                    PhotoEditorScreen(
                        onBack = { navController.popBackStack() },
                        onExport = { navController.navigate(Routes.EXPORT) },
                    )
                }
                composable(Routes.COLLAGE_BUILDER) {
                    CollageScreen(
                        onBack = { navController.popBackStack() },
                        onExport = {
                            navController.navigate(Routes.EXPORT) {
                                popUpTo(Routes.COLLAGE_BUILDER) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.EXPORT) {
                    ExportScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(700)
        onFinished()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(SnapzyColors.bgBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Snapzy", style = SnapzyType.display, color = SnapzyColors.brandPrimary)
    }
}

/** Navigate to [route] and clear everything behind it, so back doesn't return to splash/onboarding. */
private fun NavHostController.navigateReplacingStart(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
    }
}
