package app.bikriflow.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.bikriflow.mobile.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase(application)
    private val repository = Repository(database)
    private val settingsStore = SettingsStore(application)
    private val _settings = MutableStateFlow(settingsStore.read())
    val settings: StateFlow<BusinessSettings> = _settings.asStateFlow()
    private val _metrics = MutableStateFlow(DashboardMetrics())
    val metrics: StateFlow<DashboardMetrics> = _metrics.asStateFlow()
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()
    private val _customers = MutableStateFlow<List<CustomerSummary>>(emptyList())
    val customers: StateFlow<List<CustomerSummary>> = _customers.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    init { refreshAll() }
    fun completeOnboarding(businessName: String, currency: String) { val next = _settings.value.copy(onboardingComplete = true, businessName = businessName.trim().ifBlank { "My Store" }, currency = currency); settingsStore.save(next); _settings.value = next }
    fun updateSettings(next: BusinessSettings) { settingsStore.save(next); _settings.value = next }
    private suspend fun loadAll() { _metrics.value = repository.dashboard(); _orders.value = repository.orders(); _products.value = repository.products(); _customers.value = repository.customers() }
    fun refreshAll() = viewModelScope.launch { _busy.value = true; try { loadAll() } finally { _busy.value = false } }
    fun refreshOrders(query: String = "", status: OrderStatus? = null) = viewModelScope.launch { _orders.value = repository.orders(query, status) }
    fun refreshProducts(query: String = "") = viewModelScope.launch { _products.value = repository.products(query) }
    fun saveProduct(product: Product, onDone: () -> Unit = {}) = viewModelScope.launch { repository.saveProduct(product); loadAll(); onDone() }
    fun archiveProduct(id: Long) = viewModelScope.launch { repository.archiveProduct(id); loadAll() }
    fun createOrder(order: Order, onDone: (Long) -> Unit = {}) = viewModelScope.launch { _busy.value = true; try { val id = repository.createOrder(order); loadAll(); onDone(id) } finally { _busy.value = false } }
    fun updateStatus(id: Long, status: OrderStatus) = viewModelScope.launch { repository.updateStatus(id, status); loadAll() }
    suspend fun order(id: Long): Order? = repository.order(id)
}
