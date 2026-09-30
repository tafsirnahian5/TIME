package com.example.timeapp.ui.components

import android.content.Context
import android.content.Intent

/**
 * Relaunches the app in a fresh process — after a restore (the database was swapped under it)
 * and after a language change (text built once, like enum labels, must be built again).
 */
fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
