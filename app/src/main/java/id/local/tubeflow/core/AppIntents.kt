package id.local.tubeflow.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import id.local.tubeflow.MainActivity

object AppIntents {
    fun open(context: Context, destination: AppDestination): Intent =
        Intent(context, MainActivity::class.java).setAction(destination.action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun pending(context: Context, destination: AppDestination): PendingIntent =
        PendingIntent.getActivity(context, destination.ordinal + 100, open(context, destination),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
