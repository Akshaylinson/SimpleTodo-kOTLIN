package com.akshay.simpletodo

import java.util.UUID

data class Todo(
    val id: String = UUID.randomUUID().toString(),   // Use UUID as unique string ID
    var text: String,
    var done: Boolean = false,
    var priority: Int = 0,
    var starred: Boolean = false,
    var dueMillis: Long? = null
)

