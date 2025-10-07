package com.akshay.simpletodo

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.akshay.simpletodo.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val todos = mutableListOf<Todo>()
    private lateinit var adapter: TodoAdapter
    private val gson = Gson()
    private val prefsKey = "todo_list_v1"
    private val tag = "simpletodo"

    // permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.notifications_disabled), Toast.LENGTH_LONG).show()
            }
        }

    // voice input launcher
    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!matches.isNullOrEmpty()) {
            binding.inputTodo.setText(matches[0])
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request notification permission if needed (Android 13+)
        ensureNotificationPermissionIfNeeded()
        ensureExactAlarmPermissionIfNeeded()
        loadTodos()

        adapter = TodoAdapter(
            list = todos,
            onToggle = { pos -> toggleDone(pos) },
            onLongPress = { pos -> removeWithUndo(pos) },
            onEdit = { pos -> openEditDialog(pos) },
            onStar = { pos -> toggleStar(pos) }
        )

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        // 🔹 Empty State Handling
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = checkEmptyState()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = checkEmptyState()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = checkEmptyState()
        })
        checkEmptyState()

        // Swipe & drag setup
        attachTouchHelpers()

        binding.btnAdd.setOnClickListener {
            val text = binding.inputTodo.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_something), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openAddDialog(prefill = text)
            binding.inputTodo.setText("")
        }

        binding.inputTodo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnAdd.performClick()
                true
            } else false
        }

        // voice button
        try {
            binding.voiceBtn.setOnClickListener {
                startVoiceInput()
            }
        } catch (_: Exception) {
            Log.d(tag, "voiceBtn not present in layout")
        }
    }

    // 🔹 Empty State View Logic
    private fun checkEmptyState() {
        val emptyState = findViewById<LinearLayout>(R.id.emptyState)
        if (todos.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            binding.recycler.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            binding.recycler.visibility = View.VISIBLE
        }
    }

    private fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val has = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!has) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureExactAlarmPermissionIfNeeded() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                try {
                    val i = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(i)
                } catch (ex: Exception) {
                    Log.w(tag, "Unable to open exact alarm settings: ${ex.message}")
                }
            }
        }
    }

    private fun startVoiceInput() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }
        try {
            voiceLauncher.launch(i)
        } catch (ex: Exception) {
            Toast.makeText(this, getString(R.string.voice_not_supported), Toast.LENGTH_SHORT).show()
        }
    }

    // ------- persistence -------
    private fun saveTodos() {
        val json = gson.toJson(todos)
        getSharedPreferences("simpletodo", Context.MODE_PRIVATE).edit {
            putString(prefsKey, json)
        }
    }

    // ------- robust JSON loader -------
    private fun loadTodos() {
        val sp = getSharedPreferences("simpletodo", Context.MODE_PRIVATE)
        val json = sp.getString(prefsKey, null) ?: return

        try {
            val jsonElement = com.google.gson.JsonParser.parseString(json)
            if (!jsonElement.isJsonArray) {
                Log.w(tag, "Saved todos JSON not an array, clearing prefs.")
                sp.edit().remove(prefsKey).apply()
                return
            }

            val arr = jsonElement.asJsonArray
            todos.clear()

            for (je in arr) {
                if (!je.isJsonObject) continue
                val obj = je.asJsonObject

                val id = when {
                    obj.has("id") && obj.get("id").isJsonPrimitive && obj.get("id").asJsonPrimitive.isNumber ->
                        obj.get("id").asNumber.toLong().toString()
                    obj.has("id") && obj.get("id").isJsonPrimitive ->
                        obj.get("id").asString
                    else -> UUID.randomUUID().toString()
                }

                val text = if (obj.has("text") && obj.get("text").isJsonPrimitive) obj.get("text").asString else ""
                val done = if (obj.has("done") && obj.get("done").isJsonPrimitive) obj.get("done").asBoolean else false
                val priority = if (obj.has("priority") && obj.get("priority").isJsonPrimitive) {
                    try { obj.get("priority").asInt } catch (_: Exception) { 0 }
                } else 0
                val starred = if (obj.has("starred") && obj.get("starred").isJsonPrimitive) obj.get("starred").asBoolean else false

                val dueMillis: Long? = if (obj.has("dueMillis") && !obj.get("dueMillis").isJsonNull) {
                    try {
                        if (obj.get("dueMillis").isJsonPrimitive && obj.get("dueMillis").asJsonPrimitive.isNumber) {
                            obj.get("dueMillis").asLong
                        } else {
                            val s = obj.get("dueMillis").asString
                            s.toLongOrNull()
                        }
                    } catch (e: Exception) { null }
                } else null

                val todo = Todo(
                    id = id,
                    text = text,
                    done = done,
                    priority = priority,
                    starred = starred,
                    dueMillis = dueMillis
                )
                todos.add(todo)
            }

            Log.d(tag, "Loaded ${todos.size} todos (migrated/robust load).")
        } catch (ex: Exception) {
            Log.w(tag, "Failed to parse saved todos JSON, clearing prefs. error=${ex.message}")
            sp.edit().remove(prefsKey).apply()
            todos.clear()
        }
    }

    // ------- dialogs -------
    private fun openAddDialog(prefill: String? = null) {
        val edit = EditText(this)
        edit.setText(prefill ?: "")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(edit)
        }

        val dueBtn = androidx.appcompat.widget.AppCompatButton(this).apply {
            text = getString(R.string.set_due_optional)
        }
        layout.addView(dueBtn)
        var chosenDueMillis: Long? = null
        dueBtn.setOnClickListener {
            showSimpleDatePicker { millis ->
                chosenDueMillis = millis
                val formatted = java.text.SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(millis))
                dueBtn.text = getString(R.string.due_colon, formatted)
            }
        }

        val radio = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            val normal = androidx.appcompat.widget.AppCompatRadioButton(this@MainActivity).apply {
                text = getString(R.string.priority_normal)
                id = View.generateViewId()
            }
            val high = androidx.appcompat.widget.AppCompatRadioButton(this@MainActivity).apply {
                text = getString(R.string.priority_high)
                id = View.generateViewId()
            }
            val urgent = androidx.appcompat.widget.AppCompatRadioButton(this@MainActivity).apply {
                text = getString(R.string.priority_urgent)
                id = View.generateViewId()
            }
            addView(normal); addView(high); addView(urgent)
            if (childCount > 0) check(getChildAt(0).id)
        }
        layout.addView(radio)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_todo_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val text = edit.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                val pr = when (radio.checkedRadioButtonId) {
                    radio.getChildAt(1)?.id -> 1
                    radio.getChildAt(2)?.id -> 2
                    else -> 0
                }
                val todo = Todo(text = text, priority = pr, dueMillis = chosenDueMillis)
                todos.add(0, todo)
                adapter.notifyItemInserted(0)
                binding.recycler.scrollToPosition(0)
                saveTodos()
                scheduleAlarmIfNeeded(todo)
                checkEmptyState()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openEditDialog(pos: Int) {
        val t = todos[pos]
        val edit = EditText(this)
        edit.setText(t.text)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(edit)
        }

        val dueBtn = androidx.appcompat.widget.AppCompatButton(this).apply {
            text = t.dueMillis?.let {
                val formatted = java.text.SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(it))
                getString(R.string.due_colon, formatted)
            } ?: getString(R.string.set_due_optional)
        }
        layout.addView(dueBtn)
        var chosenDue: Long? = t.dueMillis
        dueBtn.setOnClickListener {
            showSimpleDatePicker(t.dueMillis) { millis ->
                chosenDue = millis
                val formatted = java.text.SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(millis))
                dueBtn.text = getString(R.string.due_colon, formatted)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_todo_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newText = edit.text.toString().trim()
                if (newText.isEmpty()) return@setPositiveButton
                t.text = newText
                t.dueMillis = chosenDue
                adapter.notifyItemChanged(pos)
                saveTodos()
                scheduleAlarmIfNeeded(t)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showSimpleDatePicker(initialDate: Long? = null, onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        initialDate?.let { calendar.timeInMillis = it }

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                val timePicker = TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute)
                        onDateSelected(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                )
                timePicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // ------- Todo controls -------
    private fun toggleDone(pos: Int) {
        val t = todos[pos]
        t.done = !t.done
        adapter.notifyItemChanged(pos)
        saveTodos()
    }

    private fun toggleStar(pos: Int) {
        val t = todos[pos]
        t.starred = !t.starred
        adapter.notifyItemChanged(pos)
        saveTodos()
    }

    private fun removeWithUndo(pos: Int) {
        val removed = adapter.removeAt(pos)
        saveTodos()
        cancelAlarm(removed)
        checkEmptyState()

        Snackbar.make(binding.root, getString(R.string.deleted_text), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.undo)) {
                adapter.insertAt(pos, removed)
                saveTodos()
                scheduleAlarmIfNeeded(removed)
                checkEmptyState()
            }.show()
    }

    private fun attachTouchHelpers() {
        val itemTouch = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.moveItem(from, to)
                saveTodos()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                removeWithUndo(pos)
            }
        }
        ItemTouchHelper(itemTouch).attachToRecyclerView(binding.recycler)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarmIfNeeded(todo: Todo) {
        val millis = todo.dueMillis ?: return
        if (millis <= System.currentTimeMillis()) return

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("todo_text", todo.text)
            putExtra("todo_id", todo.id)
        }
        val requestCode = todo.id.hashCode()
        val pi = PendingIntent.getBroadcast(
            this, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancelAlarm(todo: Todo) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(this, AlarmReceiver::class.java)
        val requestCode = todo.id.hashCode()
        val pi = PendingIntent.getBroadcast(
            this, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    override fun onPause() {
        super.onPause()
        saveTodos()
    }
}

