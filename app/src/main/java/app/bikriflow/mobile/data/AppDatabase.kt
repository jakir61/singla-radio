package app.bikriflow.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "bikriflow.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,sku TEXT DEFAULT '',variant TEXT DEFAULT '',sale_price REAL DEFAULT 0,cost_price REAL DEFAULT 0,stock INTEGER DEFAULT 0,low_stock_at INTEGER DEFAULT 3,active INTEGER DEFAULT 1)")
        db.execSQL("CREATE TABLE orders(id INTEGER PRIMARY KEY AUTOINCREMENT,order_no TEXT NOT NULL,customer_name TEXT NOT NULL,phone TEXT DEFAULT '',address TEXT DEFAULT '',channel TEXT DEFAULT 'Facebook',payment_method TEXT DEFAULT 'COD',status TEXT DEFAULT 'NEW',customer_delivery_charge REAL DEFAULT 0,courier_cost REAL DEFAULT 0,packaging_cost REAL DEFAULT 0,discount REAL DEFAULT 0,advance REAL DEFAULT 0,cod_fee REAL DEFAULT 0,other_expense REAL DEFAULT 0,note TEXT DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE order_items(id INTEGER PRIMARY KEY AUTOINCREMENT,order_id INTEGER NOT NULL,product_id INTEGER,name TEXT NOT NULL,variant TEXT DEFAULT '',quantity INTEGER DEFAULT 1,sale_price REAL DEFAULT 0,unit_cost REAL DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getProducts(query: String = ""): List<Product> {
        val sql = if (query.isBlank()) "SELECT * FROM products WHERE active=1 ORDER BY name" else "SELECT * FROM products WHERE active=1 AND (name LIKE ? OR sku LIKE ?) ORDER BY name"
        val args = if (query.isBlank()) null else arrayOf("%" + query.trim() + "%", "%" + query.trim() + "%")
        readableDatabase.rawQuery(sql, args).use { c ->
            val out = mutableListOf<Product>()
            while (c.moveToNext()) out += Product(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getDouble(5), c.getInt(6), c.getInt(7), c.getInt(8) == 1)
            return out
        }
    }

    fun upsertProduct(p: Product): Long {
        val v = ContentValues().apply {
            put("name", p.name); put("sku", p.sku); put("variant", p.variant); put("sale_price", p.salePrice)
            put("cost_price", p.costPrice); put("stock", p.stock); put("low_stock_at", p.lowStockAt); put("active", if (p.active) 1 else 0)
        }
        return if (p.id == 0L) writableDatabase.insertOrThrow("products", null, v)
        else { writableDatabase.update("products", v, "id=?", arrayOf(p.id.toString())); p.id }
    }

    fun archiveProduct(id: Long) { writableDatabase.execSQL("UPDATE products SET active=0 WHERE id=?", arrayOf(id)) }

    fun createOrder(o: Order): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val v = ContentValues().apply {
                put("order_no", o.orderNo.ifBlank { "BF-" + now.toString().takeLast(8) })
                put("customer_name", o.customerName); put("phone", o.phone); put("address", o.address); put("channel", o.channel)
                put("payment_method", o.paymentMethod); put("status", o.status.name); put("customer_delivery_charge", o.customerDeliveryCharge)
                put("courier_cost", o.courierCost); put("packaging_cost", o.packagingCost); put("discount", o.discount); put("advance", o.advance)
                put("cod_fee", o.codFee); put("other_expense", o.otherExpense); put("note", o.note); put("created_at", now); put("updated_at", now)
            }
            val id = db.insertOrThrow("orders", null, v)
            o.items.forEach { i ->
                val iv = ContentValues().apply {
                    put("order_id", id); i.productId?.let { put("product_id", it) }; put("name", i.name); put("variant", i.variant)
                    put("quantity", i.quantity); put("sale_price", i.salePrice); put("unit_cost", i.unitCost)
                }
                db.insertOrThrow("order_items", null, iv)
                i.productId?.let { db.execSQL("UPDATE products SET stock=MAX(0,stock-?) WHERE id=?", arrayOf<Any>(i.quantity, it)) }
            }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    private fun getItems(orderId: Long): List<OrderItem> =
        readableDatabase.rawQuery("SELECT * FROM order_items WHERE order_id=? ORDER BY id", arrayOf(orderId.toString())).use { c ->
            val out = mutableListOf<OrderItem>()
            while (c.moveToNext()) out += OrderItem(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getString(3), c.getString(4), c.getInt(5), c.getDouble(6), c.getDouble(7))
            out
        }

    private fun Cursor.toOrder(): Order {
        val id = getLong(getColumnIndexOrThrow("id"))
        return Order(
            id = id, orderNo = getString(getColumnIndexOrThrow("order_no")), customerName = getString(getColumnIndexOrThrow("customer_name")),
            phone = getString(getColumnIndexOrThrow("phone")), address = getString(getColumnIndexOrThrow("address")), channel = getString(getColumnIndexOrThrow("channel")),
            paymentMethod = getString(getColumnIndexOrThrow("payment_method")), status = runCatching { OrderStatus.valueOf(getString(getColumnIndexOrThrow("status"))) }.getOrDefault(OrderStatus.NEW),
            items = getItems(id), customerDeliveryCharge = getDouble(getColumnIndexOrThrow("customer_delivery_charge")), courierCost = getDouble(getColumnIndexOrThrow("courier_cost")),
            packagingCost = getDouble(getColumnIndexOrThrow("packaging_cost")), discount = getDouble(getColumnIndexOrThrow("discount")), advance = getDouble(getColumnIndexOrThrow("advance")),
            codFee = getDouble(getColumnIndexOrThrow("cod_fee")), otherExpense = getDouble(getColumnIndexOrThrow("other_expense")), note = getString(getColumnIndexOrThrow("note")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")), updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
        )
    }

    fun getOrders(query: String = "", status: OrderStatus? = null, limit: Int = 300): List<Order> {
        val clauses = mutableListOf<String>(); val args = mutableListOf<String>()
        if (query.isNotBlank()) { clauses += "(customer_name LIKE ? OR phone LIKE ? OR order_no LIKE ?)"; repeat(3) { args += "%" + query.trim() + "%" } }
        status?.let { clauses += "status=?"; args += it.name }
        val sql = "SELECT * FROM orders" + if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ") + " ORDER BY created_at DESC LIMIT " + limit.coerceIn(1, 1000)
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            val out = mutableListOf<Order>(); while (c.moveToNext()) out += c.toOrder(); out
        }
    }

    fun getOrder(id: Long): Order? =
        readableDatabase.rawQuery("SELECT * FROM orders WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toOrder() else null }

    fun updateOrderStatus(id: Long, status: OrderStatus) {
        val v = ContentValues().apply { put("status", status.name); put("updated_at", System.currentTimeMillis()) }
        writableDatabase.update("orders", v, "id=?", arrayOf(id.toString()))
    }

    fun dashboardMetrics(): DashboardMetrics {
        val all = getOrders(limit = 1000)
        val now = Calendar.getInstance(); val year = now.get(Calendar.YEAR); val day = now.get(Calendar.DAY_OF_YEAR); val month = now.get(Calendar.MONTH)
        fun today(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) == year && it.get(Calendar.DAY_OF_YEAR) == day }
        fun month(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == month }
        val td = all.filter { today(it.createdAt) }; val goodTd = td.filterNot { it.status.restoresStock }; val goodMo = all.filter { month(it.createdAt) && !it.status.restoresStock }
        return DashboardMetrics(
            todayOrders = td.size, todaySales = goodTd.sumOf { it.revenue }, todayProfit = goodTd.sumOf { it.profit },
            pendingCod = all.filter { it.status in setOf(OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COD_PENDING) }.sumOf { it.amountDue },
            returns = td.count { it.status.restoresStock }, lowStockCount = getProducts().count { it.stock <= it.lowStockAt },
            monthSales = goodMo.sumOf { it.revenue }, monthProfit = goodMo.sumOf { it.profit }
        )
    }

    fun getCustomerSummaries(): List<CustomerSummary> =
        getOrders(limit = 1000).groupBy { it.phone.ifBlank { it.customerName } }.values.map { g ->
            CustomerSummary(g.first().customerName, g.first().phone, g.size, g.count { it.status in setOf(OrderStatus.DELIVERED, OrderStatus.PAID, OrderStatus.COD_PENDING) },
                g.count { it.status.restoresStock }, g.filterNot { it.status.restoresStock }.sumOf { it.revenue }, g.maxOf { it.createdAt })
        }.sortedByDescending { it.lastOrderAt }
}
