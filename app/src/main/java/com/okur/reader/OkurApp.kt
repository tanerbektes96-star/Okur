package com.okur.reader

import android.app.Application
import com.okur.reader.data.BookRepository
import com.okur.reader.data.SettingsRepository
import com.okur.reader.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class OkurApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val database: AppDatabase = AppDatabase.create(app)
    val books = BookRepository(app, database)
    val settings = SettingsRepository(app)

    /** Ekran kapansa bile tamamlanması gereken işler (ör. son konumu kaydetme) için */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
