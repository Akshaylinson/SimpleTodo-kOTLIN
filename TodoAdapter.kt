package com.akshay.simpletodo

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.akshay.simpletodo.databinding.ItemTodoBinding
import java.text.SimpleDateFormat
import java.util.*

class TodoAdapter(
    private val list: MutableList<Todo>,
    private val onToggle: (Int) -> Unit,
    private val onLongPress: (Int) -> Unit,
    private val onEdit: (Int) -> Unit,
    private val onStar: (Int) -> Unit
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    inner class TodoViewHolder(val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onEdit(pos)
            }
            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onLongPress(pos)
                true
            }
            binding.starBtn.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onStar(pos)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val binding = ItemTodoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TodoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvText.text = item.text

        // Strike-through for completed tasks
        holder.binding.tvText.paintFlags =
            if (item.done) holder.binding.tvText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else holder.binding.tvText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        // Star state
        val starRes = if (item.starred) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        holder.binding.starBtn.setImageResource(starRes)

        // Due date
        val due = item.dueMillis
        if (due != null) {
            val fmt = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            holder.binding.tvDue.text = "Due: ${fmt.format(Date(due))}"
            holder.binding.tvDue.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.tvDue.visibility = android.view.View.GONE
        }
    }

    override fun getItemCount(): Int = list.size

    fun moveItem(from: Int, to: Int) {
        if (from in 0 until list.size && to in 0 until list.size) {
            Collections.swap(list, from, to)
            notifyItemMoved(from, to)
        }
    }

    fun removeAt(pos: Int): Todo {
        val todo = list.removeAt(pos)
        notifyItemRemoved(pos)
        return todo
    }

    fun insertAt(pos: Int, todo: Todo) {
        list.add(pos, todo)
        notifyItemInserted(pos)
    }
}
