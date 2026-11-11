package com.movexa.app

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
import com.movexa.app.core.comingsoon.ComingSoonHost
import com.movexa.app.core.comingsoon.rememberComingSoonController
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaTheme
import com.movexa.app.core.designsystem.MovexaType
import com.movexa.app.core.navigation.Routes
import com.movexa.app.feature.collage.CollageScreen
import com.movexa.app.feature.editor.photo.PhotoEditorScreen
import com.movexa.app.feature.editor.video.VideoEditorScreen
import com.movexa.app.feature.export.ExportScreen
import com.movexa.app.core.editing.EditKind
import com.movexa.app.core.media.EditorSessionStore
import com.movexa.app.feature.home.HomeScreen
import com.movexa.app.feature.onboarding.OnboardingScreen
import com.movexa.app.feature.picker.MediaPickerScreen
import kotlinx.coroutines.delay

@Composable
fun MovexaApp() {
    MovexaTheme {
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
        modifier = Modifier.fillMaxSize().background(MovexaColors.bgBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Movexa", style = MovexaType.display, color = MovexaColors.brandPrimary)
    }
}

/** Navigate to [route] and clear everything behind it, so back doesn't return to splash/onboarding. */
private fun NavHostController.navigateReplacingStart(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
    }
}
