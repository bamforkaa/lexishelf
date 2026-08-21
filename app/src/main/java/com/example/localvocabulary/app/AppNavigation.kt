package com.example.localvocabulary.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.localvocabulary.feature.settings.SettingsScreen
import com.example.localvocabulary.feature.settings.SettingsViewModel
import com.example.localvocabulary.feature.tags.TagManagementScreen
import com.example.localvocabulary.feature.tags.TagManagementViewModel
import com.example.localvocabulary.feature.worddetail.WordDetailEffect
import com.example.localvocabulary.feature.worddetail.WordDetailScreen
import com.example.localvocabulary.feature.worddetail.WordDetailViewModel
import com.example.localvocabulary.feature.wordeditor.WordEditorEffect
import com.example.localvocabulary.feature.wordeditor.WordEditorScreen
import com.example.localvocabulary.feature.wordeditor.WordEditorViewModel
import com.example.localvocabulary.feature.wordlist.WordListScreen
import com.example.localvocabulary.feature.wordlist.WordListViewModel

private object Routes {
    const val WORDS = "words"
    const val TAGS = "tags"
    const val SETTINGS = "settings"
    const val NEW_WORD = "word/new"
    const val WORD_DETAIL = "word/{entryId}"
    const val EDIT_WORD = "word/{entryId}/edit"

    fun detail(entryId: Long) = "word/$entryId"
    fun edit(entryId: Long) = "word/$entryId/edit"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.WORDS) {
        composable(Routes.WORDS) {
            val viewModel = hiltViewModel<WordListViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WordListScreen(
                state = state,
                onAction = viewModel::onAction,
                onAddWord = { navController.navigate(Routes.NEW_WORD) },
                onOpenWord = { navController.navigate(Routes.detail(it)) },
                onManageTags = { navController.navigate(Routes.TAGS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.NEW_WORD) {
            WordEditorDestination(onBack = { navController.popBackStack() }) { entryId ->
                navController.navigate(Routes.detail(entryId)) {
                    popUpTo(Routes.WORDS)
                    launchSingleTop = true
                }
            }
        }

        composable(
            route = Routes.EDIT_WORD,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
        ) {
            WordEditorDestination(onBack = { navController.popBackStack() }) { entryId ->
                navController.navigate(Routes.detail(entryId)) {
                    popUpTo(Routes.WORDS)
                    launchSingleTop = true
                }
            }
        }

        composable(
            route = Routes.WORD_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
        ) {
            val viewModel = hiltViewModel<WordDetailViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.effects.collect { effect ->
                    if (effect == WordDetailEffect.Deleted) navController.popBackStack()
                }
            }
            WordDetailScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
            )
        }

        composable(Routes.TAGS) {
            val viewModel = hiltViewModel<TagManagementViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            TagManagementScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel = hiltViewModel<SettingsViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun WordEditorDestination(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val viewModel = hiltViewModel<WordEditorViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is WordEditorEffect.Saved) onSaved(effect.entryId)
        }
    }
    WordEditorScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}
