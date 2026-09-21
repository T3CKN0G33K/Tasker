package com.example.pantry.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantry.data.BarcodeLookup
import com.example.pantry.data.BillItem
import com.example.pantry.data.BudgetLedger
import com.example.pantry.data.FeedbackItem
import com.example.pantry.data.Household
import com.example.pantry.data.HouseholdItem
import com.example.pantry.data.InventoryRepository
import com.example.pantry.data.SupportTicket
import com.example.pantry.data.TicketMessage
import com.example.pantry.service.MyFirebaseMessagingService
import com.example.tasker.MainActivity
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: InventoryRepository = InventoryRepository()
) : ViewModel() {

    private val prefs: SharedPreferences?
        get() {
            val ctx = MainActivity.appContext ?: MyFirebaseMessagingService.instance
            return ctx?.getSharedPreferences("pantry_prefs", Context.MODE_PRIVATE)
        }

    private val savedId: String
        get() {
            val fromPrefs = prefs?.getString("householdId", null) ?: prefs?.getString("household_id", null)
            return if (fromPrefs.isNullOrEmpty() || fromPrefs == "default_household") "default_household" else fromPrefs
        }

    private val _householdId = MutableStateFlow<String?>(if (savedId == "default_household") null else savedId)
    val householdIdState: StateFlow<String?> = _householdId

    private val _isOwner = MutableStateFlow(
        prefs?.getBoolean("isOwner", false) ?: prefs?.getBoolean("is_owner", false) ?: false
    )
    val isOwner: StateFlow<Boolean> = _isOwner

    private val _showSetup = MutableStateFlow(savedId == "default_household")
    val showSetup: StateFlow<Boolean> = _showSetup.asStateFlow()

    val inviteCodeState = MutableStateFlow<String>("")

    private val _showBudgetTab = MutableStateFlow(true)
    val showBudgetTab: StateFlow<Boolean> = _showBudgetTab.asStateFlow()

    private val _pantryTabLabel = MutableStateFlow("Pantry")
    val pantryTabLabel: StateFlow<String> = _pantryTabLabel.asStateFlow()

    private val _budgetTabLabel = MutableStateFlow("Budget")
    val budgetTabLabel: StateFlow<String> = _budgetTabLabel.asStateFlow()

    private val _activeTicket = MutableStateFlow<SupportTicket?>(null)
    val activeTicket: StateFlow<SupportTicket?> = _activeTicket.asStateFlow()

    val activeTicketMessages: StateFlow<List<TicketMessage>> = _activeTicket
        .flatMapLatest { ticket ->
            val hId = _householdId.value
            if (ticket != null && !hId.isNullOrEmpty()) {
                repository.getTicketMessagesFlow(hId, ticket.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAdminUnlocked = MutableStateFlow(false)
    val isAdminUnlocked: StateFlow<Boolean> = _isAdminUnlocked.asStateFlow()

    private val _remoteLiquidGlass = MutableStateFlow(false)
    val remoteLiquidGlass: StateFlow<Boolean> = _remoteLiquidGlass.asStateFlow()

    private val _localThemeOverride = MutableStateFlow(
        prefs?.getString("theme_preference", "DEFAULT") ?: "DEFAULT"
    )
    val localThemeOverride: StateFlow<String> = _localThemeOverride.asStateFlow()

    val useLiquidGlass: StateFlow<Boolean> = combine(_remoteLiquidGlass, _localThemeOverride) { remote, local ->
        if (local == "DEFAULT") remote else local == "LIQUID_GLASS"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun verifyAdminPin(pin: String): Boolean {
        if (pin == "0000") { // Hardcoded default for now
            _isAdminUnlocked.value = true
            return true
        }
        return false
    }

    fun setThemePreference(preference: String) {
        prefs?.edit()?.putString("theme_preference", preference)?.apply()
        _localThemeOverride.value = preference
    }

    val adminTicketQueue: StateFlow<List<SupportTicket>> = _householdId
        .flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(emptyList())
            else repository.getTicketsFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        fetchRemoteConfig()
    }

    fun fetchRemoteConfig() {
        try {
            val remoteConfig = Firebase.remoteConfig
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 0
            }
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "show_budget_tab" to true,
                    "pantry_tab_label" to "Pantry",
                    "budget_tab_label" to "Budget",
                    "enable_liquid_glass" to false
                )
            )
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val value = remoteConfig.getBoolean("show_budget_tab")
                    val pantryLabel = remoteConfig.getString("pantry_tab_label")
                    val budgetLabel = remoteConfig.getString("budget_tab_label")
                    val glassEnabled = remoteConfig.getBoolean("enable_liquid_glass")

                    viewModelScope.launch(Dispatchers.Main) {
                        _showBudgetTab.value = value
                        if (pantryLabel.isNotEmpty()) _pantryTabLabel.value = pantryLabel
                        if (budgetLabel.isNotEmpty()) _budgetTabLabel.value = budgetLabel
                        _remoteLiquidGlass.value = glassEnabled
                    }
                    Log.d("RemoteConfig", "Fetched show_budget_tab: $value, glass: $glassEnabled")
                } else {
                    Log.e("RemoteConfig", "Fetch failed", task.exception)
                }
            }

            // Real-time update listener pushing updates thread-safely to Main UI thread
            remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    Log.d("RemoteConfig", "Ping received! Keys updated: ${configUpdate.updatedKeys}")
                    remoteConfig.activate().addOnCompleteListener {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (configUpdate.updatedKeys.contains("show_budget_tab")) {
                                _showBudgetTab.value = remoteConfig.getBoolean("show_budget_tab")
                            }
                            if (configUpdate.updatedKeys.contains("pantry_tab_label")) {
                                val pantryLabel = remoteConfig.getString("pantry_tab_label")
                                if (pantryLabel.isNotEmpty()) _pantryTabLabel.value = pantryLabel
                            }
                            if (configUpdate.updatedKeys.contains("budget_tab_label")) {
                                val budgetLabel = remoteConfig.getString("budget_tab_label")
                                if (budgetLabel.isNotEmpty()) _budgetTabLabel.value = budgetLabel
                            }
                            if (configUpdate.updatedKeys.contains("enable_liquid_glass")) {
                                _remoteLiquidGlass.value = remoteConfig.getBoolean("enable_liquid_glass")
                            }
                        }
                    }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    Log.e("RemoteConfig", "Real-time update failed", error)
                }
            })
        } catch (e: Exception) {
            Log.e("RemoteConfig", "RemoteConfig initialization exception: ${e.message}", e)
        }
    }

    fun refreshRemoteConfig(onComplete: () -> Unit = {}) {
        try {
            val remoteConfig = Firebase.remoteConfig
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 0
            }
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val newValue = remoteConfig.getBoolean("show_budget_tab")
                    val pantryLabel = remoteConfig.getString("pantry_tab_label")
                    val budgetLabel = remoteConfig.getString("budget_tab_label")
                    val glassEnabled = remoteConfig.getBoolean("enable_liquid_glass")

                    viewModelScope.launch(Dispatchers.Main) {
                        _showBudgetTab.value = newValue
                        if (pantryLabel.isNotEmpty()) _pantryTabLabel.value = pantryLabel
                        if (budgetLabel.isNotEmpty()) _budgetTabLabel.value = budgetLabel
                        _remoteLiquidGlass.value = glassEnabled
                        Log.d("RemoteConfig", "Manual refresh show_budget_tab: $newValue, glass: $glassEnabled")
                    }
                } else {
                    Log.e("RemoteConfig", "Manual refresh failed", task.exception)
                }
                onComplete()
            }
        } catch (e: Exception) {
            Log.e("RemoteConfig", "Manual refresh exception: ${e.message}", e)
            onComplete()
        }
    }

    val inventoryItems: StateFlow<List<HouseholdItem>> = _householdId
        .flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(emptyList())
            else repository.getInventoryFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val bills: StateFlow<List<BillItem>> = _householdId
        .flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(emptyList())
            else repository.getBillsFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val budgetLedger: StateFlow<BudgetLedger> = _householdId
        .flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(
                BudgetLedger(
                    paycheckTotal = 1250.0,
                    pendingBillsTotal = 0.0,
                    payCadence = "Bi-Weekly (Home Depot)",
                    nextPaydayText = "Wed 8:30 PM",
                    daysUntilPayday = 4
                )
            )
            else repository.getLedgerFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BudgetLedger(
                paycheckTotal = 1250.0,
                pendingBillsTotal = 0.0,
                payCadence = "Bi-Weekly (Home Depot)",
                nextPaydayText = "Wed 8:30 PM",
                daysUntilPayday = 4
            )
        )

    val feedbackItems: StateFlow<List<FeedbackItem>> = _householdId
        .flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(emptyList())
            else repository.getFeedbackFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val supportTickets: StateFlow<List<SupportTicket>> = _householdId
        .flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(emptyList())
            else repository.getTicketsFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun openTicket(ticket: SupportTicket) {
        _activeTicket.value = ticket
        val hId = _householdId.value ?: return

        // If the Owner opens a WAITING ticket, instantly trigger the CONNECTED handshake
        if (_isOwner.value && ticket.status == "WAITING") {
            viewModelScope.launch {
                repository.updateTicketStatus(hId, ticket.id, "CONNECTED")
                _activeTicket.value = ticket.copy(status = "CONNECTED")
            }
        }
    }

    fun closeTicketChat() {
        _activeTicket.value = null
    }

    fun markTicketResolved() {
        val hId = _householdId.value ?: return
        val tId = _activeTicket.value?.id ?: return
        viewModelScope.launch {
            repository.updateTicketStatus(hId, tId, "CLOSED")
            _activeTicket.value = _activeTicket.value?.copy(status = "CLOSED")
        }
    }

    fun sendChatMessage(text: String) {
        val hId = _householdId.value ?: return
        val tId = _activeTicket.value?.id ?: return
        viewModelScope.launch {
            val uid = repository.getCurrentUserId()
            val message = TicketMessage(
                senderId = uid,
                senderRole = if (_isOwner.value) "Owner" else "Member",
                text = text
            )
            repository.sendMessage(hId, tId, message)
        }
    }

    fun getTicketMessages(ticketId: String): Flow<List<TicketMessage>> {
        val currentId = _householdId.value ?: return flowOf(emptyList())
        return repository.getTicketMessagesFlow(currentId, ticketId)
    }

    val isFetchingProduct = MutableStateFlow(false)

    fun createHousehold(onSuccess: (Household) -> Unit = {}) {
        viewModelScope.launch {
            val uid = repository.getCurrentUserId()
            val household = repository.createHousehold(uid)
            
            // Explicit Permanent Write to Disk
            prefs?.edit()
                ?.putString("householdId", household.id)
                ?.putString("household_id", household.id)
                ?.putBoolean("isOwner", true)
                ?.putBoolean("is_owner", true)
                ?.apply()

            repository.saveHouseholdState(household.id, isOwner = true)
            _isOwner.value = true
            _householdId.value = household.id
            inviteCodeState.value = household.inviteCode
            _showSetup.value = false
            onSuccess(household)
        }
    }

    fun joinHousehold(code: String, onSuccess: (Household) -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val uid = repository.getCurrentUserId()
            val result = repository.joinHouseholdByCode(uid, code)
            result.onSuccess { household ->
                val owner = household.isUserOwner(uid)
                
                // Explicit Permanent Write to Disk
                prefs?.edit()
                    ?.putString("householdId", household.id)
                    ?.putString("household_id", household.id)
                    ?.putBoolean("isOwner", owner)
                    ?.putBoolean("is_owner", owner)
                    ?.apply()

                repository.saveHouseholdState(household.id, isOwner = owner)
                _isOwner.value = owner
                _householdId.value = household.id
                inviteCodeState.value = household.inviteCode
                _showSetup.value = false
                onSuccess(household)
            }.onFailure { error ->
                onError(error.message ?: "Failed to join household")
            }
        }
    }

    fun signOutHousehold() {
        signOut()
    }

    fun signOut() {
        prefs?.edit()
            ?.putString("householdId", "default_household")
            ?.putString("household_id", "default_household")
            ?.putBoolean("isOwner", false)
            ?.putBoolean("is_owner", false)
            ?.apply()

        repository.saveHouseholdState("default_household", isOwner = false)
        _householdId.value = null
        _isOwner.value = false
        _showSetup.value = true
        inviteCodeState.value = ""
    }

    fun onBarcodeScanned(upc: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            isFetchingProduct.value = true
            val title = BarcodeLookup.lookupUpc(upc)
            isFetchingProduct.value = false
            onResult(title)
        }
    }

    fun addItem(
        name: String,
        quantity: Int,
        threshold: Int,
        barcode: String = "",
        isStaple: Boolean = true
    ) {
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            val status = when {
                quantity <= 0 -> "EMPTY"
                quantity <= threshold -> "LOW"
                else -> "STOCKED"
            }
            val newItem = HouseholdItem(
                name = name,
                currentQuantity = quantity,
                alertThreshold = threshold,
                status = status,
                barcode = barcode,
                isStaple = isStaple
            )
            repository.addItem(currentId, newItem)
        }
    }

    fun editItem(item: HouseholdItem) {
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            repository.editItem(currentId, item)
        }
    }

    fun deleteItem(item: HouseholdItem) {
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            repository.deleteItem(currentId, item.id)
        }
    }

    fun updateQuantity(item: HouseholdItem, delta: Int) {
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            val newQuantity = maxOf(0, item.currentQuantity + delta)
            repository.updateItemQuantity(currentId, item.id, newQuantity)
        }
    }

    fun updatePaycheck(paycheckAmount: Double) {
        if (!_isOwner.value) return // Restricted to Household Owner
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            repository.updatePaycheck(currentId, paycheckAmount)
        }
    }

    fun addBill(name: String, amount: Double, dueDate: String, dueInCurrentCycle: Boolean = true) {
        if (!_isOwner.value) return // Restricted to Household Owner
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            val bill = BillItem(
                name = name,
                amount = amount,
                isPaid = false,
                dueDate = dueDate,
                dueInCurrentCycle = dueInCurrentCycle
            )
            repository.addBill(currentId, bill)
        }
    }

    fun toggleBillPaid(bill: BillItem) {
        if (!_isOwner.value) return // Restricted to Household Owner
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            repository.toggleBillPaid(currentId, bill.id)
        }
    }

    fun toggleBillCycle(bill: BillItem) {
        if (!_isOwner.value) return // Restricted to Household Owner
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            repository.toggleBillCycle(currentId, bill.id)
        }
    }

    fun deleteBill(bill: BillItem) {
        if (!_isOwner.value) return // Restricted to Household Owner
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            repository.deleteBill(currentId, bill.id)
        }
    }

    fun submitFeedback(type: String, title: String, description: String) {
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            val feedback = FeedbackItem(
                authorRole = if (_isOwner.value) "Owner" else "Member",
                type = type,
                title = title,
                description = description
            )
            repository.submitFeedback(currentId, feedback)
        }
    }

    fun createSupportTicket(type: String, title: String, description: String, onCreated: (SupportTicket) -> Unit = {}) {
        val currentId = _householdId.value ?: return
        val uid = repository.getCurrentUserId()
        viewModelScope.launch {
            val ticket = SupportTicket(
                authorId = uid,
                authorRole = if (_isOwner.value) "Owner" else "Member",
                type = type,
                title = title,
                description = description,
                status = "WAITING"
            )
            val created = repository.createSupportTicket(currentId, ticket)
            onCreated(created)
        }
    }

    fun sendTicketMessage(ticketId: String, text: String) {
        val currentId = _householdId.value ?: return
        val uid = repository.getCurrentUserId()
        viewModelScope.launch {
            val msg = TicketMessage(
                senderId = uid,
                senderRole = if (_isOwner.value) "Owner" else "Member",
                text = text
            )
            repository.sendMessageToTicket(currentId, ticketId, msg)
        }
    }

    fun updateTicketStatus(ticketId: String, newStatus: String) {
        val currentId = _householdId.value ?: return
        viewModelScope.launch {
            repository.updateTicketStatus(currentId, ticketId, newStatus)
        }
    }
}
