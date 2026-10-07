package app.bikriflow.mobile.util
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.bikriflow.mobile.data.Order
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object CsvExporter { fun export(context: Context, orders: List<Order>): File { val dir = File(context.filesDir, "exports").apply { mkdirs() }; val file = File(dir, "bikriflow-orders-${System.currentTimeMillis()}.csv"); file.bufferedWriter().use { w -> w.appendLine("Order,Date,Customer,Phone,Channel,Status,Revenue,Cost,Profit,Due"); val df = SimpleDateFormat("yyyy-MM-dd", Locale.US); orders.forEach { o -> fun q(v: String) = "\"${v.replace("\"", "\"\"")}\""; w.appendLine(listOf(q(o.orderNo), q(df.format(Date(o.createdAt))), q(o.customerName), q(o.phone), q(o.channel), q(o.status.label), o.revenue.toString(), o.productCost.toString(), o.profit.toString(), o.amountDue.toString()).joinToString(",")) } }; return file }; fun share(context: Context, file: File) { val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Export orders")) } }
