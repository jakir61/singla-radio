package app.bikriflow.mobile.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.bikriflow.mobile.data.Order
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun export(context: Context, orders: List<Order>): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "bikriflow-orders-" + System.currentTimeMillis() + ".csv")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun quote(value: String): String = """ + value.replace(""", """") + """

        file.bufferedWriter().use { writer ->
            writer.appendLine("Order,Date,Customer,Phone,Channel,Status,Revenue,Cost,Profit,Due")
            orders.forEach { order ->
                val row = listOf(
                    quote(order.orderNo),
                    quote(dateFormat.format(Date(order.createdAt))),
                    quote(order.customerName),
                    quote(order.phone),
                    quote(order.channel),
                    quote(order.status.label),
                    order.revenue.toString(),
                    order.productCost.toString(),
                    order.profit.toString(),
                    order.amountDue.toString()
                )
                writer.appendLine(row.joinToString(","))
            }
        }
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export orders"))
    }
}
