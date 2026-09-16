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

// one row of the expense history list
data class ExpenseListItem(
    val expense: Expense,
    val categoryName: String
)

// fills the expense history list with rows
class ExpenseAdapter(
    private val imageScope: CoroutineScope,
    private val onViewPhoto: (ExpenseListItem) -> Unit,
    private val onDelete: (ExpenseListItem) -> Unit
) : ListAdapter<ExpenseListItem, ExpenseAdapter.ExpenseViewHolder>(DIFF_CALLBACK) {
    // creates a new empty row
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)

        return ExpenseViewHolder(view)
    }

    // puts one expense into a row
    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // stops loading a photo for a row that is being reused
    override fun onViewRecycled(holder: ExpenseViewHolder) {
        super.onViewRecycled(holder)
        holder.clearPendingImage()
    }

    // holds the views that make up one row
    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDescription: TextView = itemView.findViewById(R.id.tvExpenseDescription)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvExpenseCategory)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvExpenseAmount)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvExpenseDateTime)
        private val imgPhoto: ImageView = itemView.findViewById(R.id.imgExpensePhoto)
        private val btnViewPhoto: MaterialButton = itemView.findViewById(R.id.btnViewPhoto)
        private val btnDeleteExpense: MaterialButton =
            itemView.findViewById(R.id.btnDeleteExpense)

        // the photo load running for this row
        private var imageJob: Job? = null

        // shows the details of one expense in the row
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

            // delete button for this expense
            btnDeleteExpense.setOnClickListener { onDelete(item) }

            bindPhoto(item)
        }

        // shows the photo thumbnail when the expense has one
        private fun bindPhoto(item: ExpenseListItem) {
            clearPendingImage()

            val photoUri = item.expense.photoUri

            // no photo so hide the photo views
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

            // tag the row so a slow load cannot land on the wrong expense
            imgPhoto.tag = photoUri
            imgPhoto.setImageDrawable(null)

            // load the photo off the main thread
            imageJob = imageScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    PhotoLoader.loadScaled(
                        context = itemView.context,
                        uri = photoUri.toUri(),
                        targetWidth = THUMBNAIL_WIDTH,
                        targetHeight = THUMBNAIL_HEIGHT
                    )
                }

                // only use the photo if the row still shows the same expense
                if (imgPhoto.tag == photoUri) {
                    if (bitmap != null) {
                        imgPhoto.setImageBitmap(bitmap)
                    } else {
                        imgPhoto.visibility = View.GONE
                    }
                }
            }
        }

        // cancels a photo load that is no longer needed
        fun clearPendingImage() {
            imageJob?.cancel()
            imageJob = null
        }
    }

    // values shared by every row
    companion object {
        private const val THUMBNAIL_WIDTH = 600
        private const val THUMBNAIL_HEIGHT = 400

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // works out which rows changed when the list updates
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