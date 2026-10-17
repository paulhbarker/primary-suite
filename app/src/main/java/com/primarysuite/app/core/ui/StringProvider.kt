package com.primarysuite.app.core.ui

import android.content.Context
import androidx.annotation.StringRes

/** Resolves string resources. View models use it so tests can run without Android resources. */
fun interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
}

class AndroidStringProvider(context: Context) : StringProvider {
    private val appContext = context.applicationContext
    override fun get(id: Int, vararg args: Any): String = appContext.getString(id, *args)
}
