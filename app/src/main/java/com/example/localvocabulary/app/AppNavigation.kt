package com.example.localvocabulary.app

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.example.localvocabulary.dictionary.reference.ExternalDictionaryIntentFactory
import com.example.localvocabulary.feature.backup.BackupScreen
import com.example.localvocabulary.feature.backup.BackupViewModel
import com.example.localvocabulary.feature.settings.SettingsScreen
import com.example.localvocabulary.feature.settings.SettingsAction
import com.example.localvocabulary.feature.settings.SettingsViewModel
import com.example.localvocabulary.feature.handwriting.HandwritingInputViewModel
import com.example.localvocabulary.feature.tags.TagManagementScreen
import com.example.localvocabulary.feature.tags.TagManagementViewModel
import com.example.localvocabulary.feature.worddetail.WordDetailEffect
import com.example.localvocabulary.feature.worddetail.WordDetailScreen
import com.example.localvocabulary.feature.worddetail.WordDetailViewModel
import com.example.localvocabulary.feature.wordeditor.WordEditorEffect
import com.example.localvocabulary.feature.wordeditor.WordEditorScreen
import com.example.localvocabulary.feature.wordeditor.WordEditorViewModel
import com.example.localvocabulary.feature.wordeditor.WordEditorAction
import com.example.localvocabulary.feature.wordlist.WordListScreen
import com.example.localvocabulary.feature.wordlist.WordListAction
import com.example.localvocabulary.feature.wordlist.WordListViewModel
import com.example.localvocabulary.feature.wordbooks.WordbookManagementScreen
import com.example.localvocabulary.feature.wordbooks.WordbookManagementViewModel
import com.example.localvocabulary.feature.wordbooks.WordbookDetailScreen
import com.example.localvocabulary.feature.wordbooks.WordbookDetailViewModel
import com.example.localvocabulary.feature.writingpractice.PracticeInputMode
import com.example.localvocabulary.feature.writingpractice.PracticePhase
import com.example.localvocabulary.feature.writingpractice.WritingPracticeAction
import com.example.localvocabulary.feature.writingpractice.WritingPracticeScreen
import com.example.localvocabulary.feature.writingpractice.WritingPracticeViewModel
import com.example.localvocabulary.handwriting.domain.appendHandwritingCandidate

private object Routes {
    const val WORDS = "words"
    const val TAGS = "tags"
    const val WORDBOOKS = "wordbooks"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val TAG_COLLECTION = "tag/{tagId}"
    const val WORDBOOK_COLLECTION = "wordbook/{wordbookId}"
    const val NEW_WORD = "word/new"
    const val WORD_DETAIL = "word/{entryId}"
    const val EDIT_WORD = "word/{entryId}/edit"
    const val WRITING_PRACTICE = "practice"
    const val WORDBOOK_WRITING_PRACTICE = "practice/wordbook/{practiceWordbookId}"

    fun detail(entryId: Long) = "word/$entryId"
    fun edit(entryId: Long) = "word/$entryId/edit"
    fun tagCollection(tagId: Long) = "tag/$tagId"
    fun wordbookCollection(wordbookId: Long) = "wordbook/$wordbookId"
    fun wordbookWritingPractice(wordbookId: Long) = "practice/wordbook/$wordbookId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.WORDS) {
        composable(Routes.WORDS) {
            WordListDestination(
                onAddWord = { navController.navigate(Routes.NEW_WORD) },
                onOpenWord = { navController.navigate(Routes.detail(it)) },
                onManageTags = { navController.navigate(Routes.TAGS) },
                onManageWordbooks = { navController.navigate(Routes.WORDBOOKS) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenWritingPractice = { navController.navigate(Routes.WRITING_PRACTICE) },
            )
        }

        composable(
            route = Routes.TAG_COLLECTION,
            arguments = listOf(navArgument("tagId") { type = NavType.LongType }),
        ) {
            WordListDestination(
                onAddWord = { navController.navigate(Routes.NEW_WORD) },
                onOpenWord = { navController.navigate(Routes.detail(it)) },
                onManageTags = { navController.navigate(Routes.TAGS) },
                onManageWordbooks = { navController.navigate(Routes.WORDBOOKS) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenWritingPractice = { navController.navigate(Routes.WRITING_PRACTICE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.WORDBOOK_COLLECTION,
            arguments = listOf(navArgument("wordbookId") { type = NavType.LongType }),
        ) {
            val viewModel = hiltViewModel<WordbookDetailViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WordbookDetailScreen(
                state = state,
                onAction = viewModel::onAction,
                onOpenWord = { navController.navigate(Routes.detail(it)) },
                onStartWritingPractice = {
                    state.wordbook?.id?.let { wordbookId ->
                        navController.navigate(Routes.wordbookWritingPractice(wordbookId))
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.WRITING_PRACTICE) {
            WritingPracticeDestination(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.WORDBOOK_WRITING_PRACTICE,
            arguments = listOf(
                navArgument("practiceWordbookId") { type = NavType.LongType },
            ),
        ) {
            WritingPracticeDestination(onBack = { navController.popBackStack() })
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
                onOpenTag = { navController.navigate(Routes.tagCollection(it)) },
                onOpenWordbook = { navController.navigate(Routes.wordbookCollection(it)) },
            )
        }

        composable(Routes.TAGS) {
            val viewModel = hiltViewModel<TagManagementViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            TagManagementScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenTag = { navController.navigate(Routes.tagCollection(it)) },
            )
        }


        composable(Routes.WORDBOOKS) {
            val viewModel = hiltViewModel<WordbookManagementViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WordbookManagementScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
                onOpenWordbook = { navController.navigate(Routes.wordbookCollection(it)) },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel = hiltViewModel<SettingsViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val packPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let { viewModel.onAction(SettingsAction.InstallDictionaryPack(it.toString())) }
            }
            SettingsScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
                onChooseDictionaryPack = {
                    packPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                },
            )
        }

        composable(Routes.BACKUP) {
            val viewModel = hiltViewModel<BackupViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            BackupScreen(
                state = state,
                onAction = viewModel::onAction,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun WordListDestination(
    onAddWord: () -> Unit,
    onOpenWord: (Long) -> Unit,
    onManageTags: () -> Unit,
    onManageWordbooks: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWritingPractice: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val viewModel = hiltViewModel<WordListViewModel>()
    val handwritingViewModel = hiltViewModel<HandwritingInputViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val handwritingState by handwritingViewModel.uiState.collectAsStateWithLifecycle()
    WordListScreen(
        state = state,
        onAction = viewModel::onAction,
        onAddWord = onAddWord,
        onOpenWord = onOpenWord,
        onManageTags = onManageTags,
        onManageWordbooks = onManageWordbooks,
        onOpenBackup = onOpenBackup,
        onOpenSettings = onOpenSettings,
        onOpenWritingPractice = onOpenWritingPractice,
        onBack = onBack,
        handwritingState = handwritingState,
        onHandwritingAction = handwritingViewModel::onAction,
        onHandwritingCandidateSelected = { candidate ->
            viewModel.onAction(WordListAction.QueryChanged(candidate))
        },
    )
}

@Composable
private fun WritingPracticeDestination(onBack: () -> Unit) {
    val viewModel = hiltViewModel<WritingPracticeViewModel>()
    val handwritingViewModel = hiltViewModel<HandwritingInputViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val handwritingState by handwritingViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(
        state.phase,
        state.handwritingSessionKey,
        state.inputMode,
        state.currentQuestion?.languageTag,
    ) {
        if (
            state.phase == PracticePhase.QUESTION &&
            state.inputMode == PracticeInputMode.HANDWRITING
        ) {
            handwritingViewModel.onAction(
                com.example.localvocabulary.feature.handwriting.HandwritingInputAction.Open(
                    contextLanguageTag = state.currentQuestion?.languageTag,
                    vocabularyLanguageTags = state.languages,
                ),
            )
        } else {
            handwritingViewModel.onAction(
                com.example.localvocabulary.feature.handwriting.HandwritingInputAction.Close,
            )
        }
    }

    WritingPracticeScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        handwritingState = handwritingState,
        onHandwritingAction = handwritingViewModel::onAction,
        onHandwritingCandidateSelected = { candidate ->
            viewModel.onAction(WritingPracticeAction.AnswerChanged(candidate))
        },
    )
}

@Composable
private fun WordEditorDestination(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = hiltViewModel<WordEditorViewModel>()
    val handwritingViewModel = hiltViewModel<HandwritingInputViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val handwritingState by handwritingViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WordEditorEffect.Saved -> onSaved(effect.entryId)
                is WordEditorEffect.OpenExternalDictionaryReference -> {
                    ExternalDictionaryIntentFactory.create(effect.uri)?.let { intent ->
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            // No browser is available; the editor remains usable.
                        }
                    }
                }
                is WordEditorEffect.OpenExistingEntry -> {
                    onSaved(effect.entryId)
                }
            }
        }
    }
    WordEditorScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        handwritingState = handwritingState,
        onHandwritingAction = handwritingViewModel::onAction,
        onHandwritingCandidateSelected = { candidate ->
            viewModel.onAction(
                WordEditorAction.HeadwordChanged(
                    appendHandwritingCandidate(state.headword, candidate),
                ),
            )
        },
    )
}
