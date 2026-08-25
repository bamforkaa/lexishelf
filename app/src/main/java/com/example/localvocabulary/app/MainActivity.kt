package com.example.localvocabulary.app

import android.os.Bundle
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.dictionary.pack.BundledDictionaryPackBootstrapState
import com.example.localvocabulary.dictionary.pack.BundledDictionaryPackBootstrapper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bundledDictionaryPackBootstrapper: BundledDictionaryPackBootstrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalVocabularyTheme {
                val packState by bundledDictionaryPackBootstrapper.state.collectAsStateWithLifecycle()
                val isDebuggable =
                    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                if (isDebuggable && packState !is BundledDictionaryPackBootstrapState.Complete) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text("Preparing local dictionaries…")
                    }
                } else {
                    AppNavigation()
                }
            }
        }
    }
}
