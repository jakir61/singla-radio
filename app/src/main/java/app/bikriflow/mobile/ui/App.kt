package app.bikriflow.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bikriflow.mobile.MainViewModel
import app.bikriflow.mobile.data.*
import app.bikriflow.mobile.util.OrderParser
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF111827)
private val Paper = Color(0xFFF7F8F3)
private val Lime = Color(0xFFC6FF4A)
private val Soft = Color(0xFFEFF1EA)
private val Muted = Color(0xFF667085)
private val Danger = Color(0xFFE5484D)
private val Success = Color(0xFF16875D)

@Composable
fun BikriFlowApp(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Color.White, secondary = Lime,
            background = Paper, surface = Color.White, onSurface = Ink
        ),
        typography = Typography()
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            if (!settings.onboardingComplete) Onboarding { name, currency -> vm.completeOnboarding(name, currency) }
            else MainShell(vm, settings)
        }
    }
}

@Composable
private fun Onboarding(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("৳") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = Ink, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("B", color = Lime, fontSize = 34.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(28.dp))
        Text("Run your shop,\nnot spreadsheets.", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text("Orders, COD, stock and real profit — private and offline.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Business name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(14.dp))
        Text("Currency", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("৳", "₹", "$", "£", "€").forEach { c ->
                FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { done(name.ifBlank { "My Store" }, currency) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(18.dp)
        ) { Text("Start selling", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))
        Text("No account • No cloud • Your data stays on this phone", color = Muted, fontSize = 12.sp)
    }
}

private enum class Tab { Home, Orders, Products, More }

@Composable
private fun MainShell(vm: MainViewModel, settings: BusinessSettings) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var addOrder by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Paper,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavItem(Tab.Home, tab, Icons.Default.Home, "Home") { tab = it }
                NavItem(Tab.Orders, tab, Icons.Default.ReceiptLong, "Orders") { tab = it }
                NavItem(Tab.Products, tab, Icons.Default.Inventory2, "Products") { tab = it }
                NavItem(Tab.More, tab, Icons.Default.MoreHoriz, "More") { tab = it }
            }
        },
        floatingActionButton = {
            if (tab != Tab.More) {
                ExtendedFloatingActionButton(
                    onClick = { addOrder = true },
                    containerColor = Lime, contentColor = Ink,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New order", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Home -> Home(vm, settings)
                Tab.Orders -> Orders(vm, settings)
                Tab.Products -> Products(vm, settings)
                Tab.More -> More(settings)
            }
        }
    }
    if (addOrder) AddOrder(vm, settings) { addOrder = false }
}

@Composable
private fun RowScope.NavItem(value: Tab, selected: Tab, icon: ImageVector, label: String, change: (Tab) -> Unit) {
    NavigationBarItem(
        selected = value == selected,
        onClick = { change(value) },
        icon = { Icon(icon, null) },
        label = { Text(label) }
    )
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

private fun money(symbol: String, value: Double): String =
    symbol + NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(value)

@Composable
private fun Home(vm: MainViewModel, settings: BusinessSettings) {
    val m by vm.metrics.collectAsState()
    val orders by vm.orders.collectAsState()
    LaunchedEffect(Unit) { vm.refreshAll() }

    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
        item { Header(settings.businessName, "Today at a glance") }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Sales", money(settings.currency, m.todaySales), Modifier.weight(1f), Color.White)
                MetricCard("Real profit", money(settings.currency, m.todayProfit), Modifier.weight(1f), Lime)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("COD pending", money(settings.currency, m.pendingCod), Modifier.weight(1f), Color.White)
                MetricCard("Orders", m.todayOrders.toString(), Modifier.weight(1f), Color.White)
            }
        }
        item { SectionTitle("Needs attention") }
        item {
            Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(22.dp)) {
                Column {
                    Attention(Icons.Default.Inventory2, "Low stock", m.lowStockCount.toString() + " products")
                    HorizontalDivider(color = Soft)
                    Attention(Icons.Default.Replay, "Returns today", m.returns.toString() + " orders")
                }
            }
        }
        item { SectionTitle("Recent orders") }
        if (orders.isEmpty()) item { EmptyState("No orders yet", "Your first order will appear here.") }
        else items(orders.take(5)) { OrderCard(it, settings.currency) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier, color: Color) {
    Surface(modifier, color = color, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(label, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Attention(icon: ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Soft, shape = RoundedCornerShape(14.dp)) { Icon(icon, null, Modifier.padding(10.dp), tint = Ink) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(value, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun Orders(vm: MainViewModel, settings: BusinessSettings) {
    val orders by vm.orders.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) { vm.refreshOrders(query) }
    Column {
        Header("Orders", "Everything you sold, in one place")
        OutlinedTextField(
            query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Search customer, phone or order") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp)
        )
        if (orders.isEmpty()) EmptyState("Nothing here", "Add an order or try another search.")
        else LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp)) {
            items(orders) { OrderCard(it, settings.currency) }
        }
    }
}

@Composable
private fun OrderCard(order: Order, currency: String) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), color = Color.White, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Soft, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.ShoppingBag, null, Modifier.padding(10.dp), tint = Ink) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(order.customerName.ifBlank { "Customer" }, fontWeight = FontWeight.Bold)
                Text(order.orderNo + " • " + order.status.label, color = Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(currency, order.revenue), fontWeight = FontWeight.Black)
                Text("Profit " + money(currency, order.profit), color = if (order.profit >= 0) Success else Danger, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Products(vm: MainViewModel, settings: BusinessSettings) {
    val products by vm.products.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshProducts() }
    Column {
        Header("Products", "Know your stock before customers ask")
        Button(
            { showAdd = true }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(16.dp)
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add product") }

        if (products.isEmpty()) EmptyState("No products yet", "Add products to track stock and profit.")
        else LazyColumn(contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(products) { p ->
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text((p.variant.ifBlank { "Standard" }) + " • " + money(settings.currency, p.salePrice), color = Muted, fontSize = 12.sp)
                        }
                        Surface(color = if (p.stock <= p.lowStockAt) Color(0xFFFFE9E9) else Soft, shape = RoundedCornerShape(12.dp)) {
                            Text(p.stock.toString() + " in stock", Modifier.padding(10.dp), color = if (p.stock <= p.lowStockAt) Danger else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showAdd) ProductDialog(vm) { showAdd = false }
}

@Composable
private fun ProductDialog(vm: MainViewModel, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var sale by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Add product", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Product name") }, singleLine = true)
                OutlinedTextField(sale, { sale = it }, label = { Text("Selling price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(cost, { cost = it }, label = { Text("Cost price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) vm.saveProduct(Product(name = name, salePrice = sale.toDoubleOrNull() ?: 0.0, costPrice = cost.toDoubleOrNull() ?: 0.0, stock = stock.toIntOrNull() ?: 0)) { close() }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } }
    )
}

@Composable
private fun More(settings: BusinessSettings) {
    Column {
        Header("More", "Simple controls. No clutter.")
        Surface(Modifier.fillMaxWidth().padding(20.dp), color = Ink, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("Private by design", color = Lime, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("BikriFlow works offline. Your orders and customer data stay on this device.", color = Color.White)
            }
        }
        SectionTitle("Business")
        InfoRow(Icons.Default.Store, "Business name", settings.businessName)
        InfoRow(Icons.Default.CurrencyExchange, "Currency", settings.currency)
        SectionTitle("About")
        InfoRow(Icons.Default.Security, "Privacy", "No account • No cloud")
        InfoRow(Icons.Default.Info, "Version", "1.0 test build")
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ink)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(value, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AddOrder(vm: MainViewModel, settings: BusinessSettings, close: () -> Unit) {
    var paste by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var courier by remember { mutableStateOf("") }
    var advance by remember { mutableStateOf("") }

    val quantity = qty.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val preview = Order(
        customerName = name,
        phone = phone,
        items = listOf(OrderItem(name = product.ifBlank { "Item" }, quantity = quantity, salePrice = price.toDoubleOrNull() ?: 0.0, unitCost = cost.toDoubleOrNull() ?: 0.0)),
        courierCost = courier.toDoubleOrNull() ?: 0.0,
        advance = advance.toDoubleOrNull() ?: 0.0
    )

    AlertDialog(
        onDismissRequest = close,
        title = { Text("New order", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(paste, { paste = it }, label = { Text("Paste customer message") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val p = OrderParser.parse(paste)
                        name = p.name; phone = p.phone; product = p.product; qty = p.quantity.toString()
                    }) {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Fill from message")
                    }
                }
                item { OutlinedTextField(name, { name = it }, label = { Text("Customer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true) }
                item { OutlinedTextField(product, { product = it }, label = { Text("Product") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(qty, { qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        OutlinedTextField(price, { price = it }, label = { Text("Sell") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(cost, { cost = it }, label = { Text("Cost") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(courier, { courier = it }, label = { Text("Courier") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(advance, { advance = it }, label = { Text("Advance") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                }
                item {
                    Surface(color = Soft, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Customer pays", color = Muted, fontSize = 12.sp)
                                Text(money(settings.currency, preview.revenue), fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Real profit", color = Muted, fontSize = 12.sp)
                                Text(money(settings.currency, preview.profit), color = if (preview.profit >= 0) Success else Danger, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && product.isNotBlank(),
                onClick = { vm.createOrder(preview) { close() } },
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text("Save order") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = Soft, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Inbox, null, Modifier.padding(16.dp), tint = Muted) }
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}
