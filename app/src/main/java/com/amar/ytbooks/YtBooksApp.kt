package com.amar.ytbooks

import android.app.Application
import android.content.Context
import com.amar.ytbooks.data.LibraryRepository
import com.amar.ytbooks.data.SettingsRepository

class YtBooksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Simple service locator – one library + settings instance for the whole process. */
object Graph {
    lateinit var app: Context
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        settings = SettingsRepository(app)
        library = LibraryRepository(app)
        initialized = true
    }
}
