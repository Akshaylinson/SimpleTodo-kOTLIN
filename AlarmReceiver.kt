package com.akshay.simpletodo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val todoText = intent.getStringExtra("todo_text") ?: "Reminder"
        Toast.makeText(context, "Reminder: $todoText", Toast.LENGTH_LONG).show()
    }
}
