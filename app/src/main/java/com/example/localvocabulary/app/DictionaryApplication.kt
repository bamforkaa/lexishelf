package com.example.localvocabulary.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.localvocabulary.dictionary.pack.BundledDictionaryPackBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class DictionaryApplication : Application() {
    @Inject
    lateinit var bundledDictionaryPackBootstrapper: BundledDictionaryPackBootstrapper

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            bundledDictionaryPackBootstrapper.start(applicationScope)
        }
    }
}
