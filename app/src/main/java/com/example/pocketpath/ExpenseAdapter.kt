package com.example.pocketpath

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pocketpath.data.entity.Expense
import com.example.pocketpath.util.Money
import com.example.pocketpath.util.PhotoLoader
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpenseListItem(
    val expense: Expense,
    val categoryName: String
)

class ExpenseAdapter(
    private val imageScope: CoroutineScope,
    private val onViewPhoto: (ExpenseListItem) -> Unit,
    private val onDelete: (ExpenseListItem) -> Unit
) : ListAdapter<ExpenseListItem, ExpenseAdapter.ExpenseViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)

        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ExpenseViewHolder) {
        super.onViewRecycled(holder)
        holder.clearPendingImage()
    }

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDescription: TextView = itemView.findViewById(R.id.tvExpenseDescription)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvExpenseCategory)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvExpenseAmount)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvExpenseDateTime)
        private val imgPhoto: ImageView = itemView.findViewById(R.id.imgExpensePhoto)
        private val btnViewPhoto: MaterialButton = itemView.findViewById(R.id.btnViewPhoto)
        private val btnDeleteExpense: MaterialButton =
            itemView.findViewById(R.id.btnDeleteExpense)

        private var imageJob: Job? = null

        fun bind(item: ExpenseListItem) {
            val expense = item.expense

            tvDescription.text = expense.description
            tvCategory.text = item.categoryName
            tvAmount.text = Money.format(expense.amount)
            tvDateTime.text = itemView.context.getString(
                R.string.expense_date_time_format,
                DATE_FORMAT.format(Date(expense.expenseDate)),
                expense.startTime,
                expense.endTime
            )

            btnDeleteExpense.setOnClickListener { onDelete(item) }

            bindPhoto(item)
        }

        private fun bindPhoto(item: ExpenseListItem) {
            clearPendingImage()

            val photoUri = item.expense.photoUri

            if (photoUri.isNullOrBlank()) {
                imgPhoto.visibility = View.GONE
                btnViewPhoto.visibility = View.GONE
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                return
            }

            imgPhoto.visibility = View.VISIBLE
            btnViewPhoto.visibility = View.VISIBLE
            btnViewPhoto.setOnClickListener { onViewPhoto(item) }
            itemView.setOnClickListener { onViewPhoto(item) }

            imgPhoto.tag = photoUri
            imgPhoto.setImageDrawable(null)

            imageJob = imageScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    PhotoLoader.loadScaled(
                        context = itemView.context,
                        uri = photoUri.toUri(),
                        targetWidth = THUMBNAIL_WIDTH,
                        targetHeight = THUMBNAIL_HEIGHT
                    )
                }

                if (imgPhoto.tag == photoUri) {
                    if (bitmap != null) {
                        imgPhoto.setImageBitmap(bitmap)
                    } else {
                        imgPhoto.visibility = View.GONE
                    }
                }
            }
        }

        fun clearPendingImage() {
            imageJob?.cancel()
            imageJob = null
        }
    }

    companion object {
        private const val THUMBNAIL_WIDTH = 600
        private const val THUMBNAIL_HEIGHT = 400

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ExpenseListItem>() {
            override fun areItemsTheSame(
                oldItem: ExpenseListItem,
                newItem: ExpenseListItem
            ): Boolean = oldItem.expense.expenseId == newItem.expense.expenseId

            override fun areContentsTheSame(
                oldItem: ExpenseListItem,
                newItem: ExpenseListItem
            ): Boolean = oldItem == newItem
        }
    }
}