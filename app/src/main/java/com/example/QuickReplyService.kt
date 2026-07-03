package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.data.AppDatabase
import com.example.data.GeminiService
import com.example.data.SavedReply
import com.example.data.SavedReplyRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs

class QuickReplyService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null

    private lateinit var repository: SavedReplyRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI state states
    private var isExpandedState = mutableStateOf(false)
    private var searchQuery = mutableStateOf("")
    private var selectedCategory = mutableStateOf("All")
    private var isGeneratingState = mutableStateOf(false)
    private var aiPrompt = mutableStateOf("")
    private var aiResponse = mutableStateOf("")

    private var repliesList = mutableStateListOf<SavedReply>()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        private const val TAG = "QuickReplyService"
        private const val CHANNEL_ID = "quick_reply_channel"
        private const val NOTIFICATION_ID = 991

        @Volatile
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val database = AppDatabase.getDatabase(applicationContext)
        repository = SavedReplyRepository(database.savedReplyDao())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Create background notification for foreground service stability
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Listen for replies in database
        serviceScope.launch {
            repository.allReplies.collectLatest { list ->
                repliesList.clear()
                repliesList.addAll(list)
            }
        }

        setupFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "QuickReply Service"
            val descriptionText = "Allows QuickReply bubble to run persistently."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QuickReply Assistant Active")
            .setContentText("Tap to manage saved replies and settings")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupFloatingView() {
        // Prepare window layout parameters
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@QuickReplyService)
            setViewTreeViewModelStoreOwner(this@QuickReplyService)
            setViewTreeSavedStateRegistryOwner(this@QuickReplyService)
            setContent {
                MyApplicationTheme {
                    FloatingWidgetUI()
                }
            }
        }

        // Apply drag listener directly to root View inside WindowManager
        composeView?.setOnTouchListener { view, event ->
            if (isExpandedState.value) {
                // If expanded, do not intercept drag at the WindowManager level
                // as the Compose view itself will handle interactions
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    params.x = initialX + deltaX.toInt()
                    params.y = initialY + deltaY.toInt()
                    windowManager.updateViewLayout(composeView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (abs(deltaX) < 10 && abs(deltaY) < 10) {
                        // Click detected!
                        toggleExpanded()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(composeView, params)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun toggleExpanded() {
        val isExpanded = !isExpandedState.value
        isExpandedState.value = isExpanded

        if (isExpanded) {
            // Expanded: Set to fill more area, make focusable so users can type search query
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            // Collapsed: Wrap content, make not focusable
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(composeView, params)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun FloatingWidgetUI() {
        val isExpanded by isExpandedState
        val query by searchQuery
        val cat by selectedCategory
        val isGenerating by isGeneratingState
        val prompt by aiPrompt
        val response by aiResponse

        val context = LocalContext.current

        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(8.dp)
        ) {
            if (!isExpanded) {
                // Collapsed: Round floating bubble
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .testTag("floating_bubble")
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quickreply,
                            contentDescription = "QuickReply Logo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                // Expanded Panel
                Card(
                    modifier = Modifier
                        .width(330.dp)
                        .height(440.dp)
                        .testTag("expanded_panel"),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BusinessCenter,
                                    contentDescription = "Business",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "QuickReply Assist",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        }
                                        context.startActivity(mainIntent)
                                        toggleExpanded()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = "Open App",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { toggleExpanded() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Minimize,
                                        contentDescription = "Minimize",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Tab Header (Replies vs AI Assist)
                        var activeTab by remember { mutableStateOf(0) } // 0: Replies, 1: AI Assist

                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                text = { Text("সংরক্ষিত (Replies)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                text = { Text("এআই অ্যাসিস্ট (AI)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                            )
                        }

                        if (activeTab == 0) {
                            // Saved Replies Tab
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                // Search Input
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { searchQuery.value = it },
                                    placeholder = { Text("খুঁজুন (Search replies...)", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery.value = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .padding(bottom = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    singleLine = true
                                )

                                // Categories list
                                val categories = listOf("All", "শুভেচ্ছা", "অর্ডার", "ডেলিভারি", "পেমেন্ট", "সহায়তা")
                                val categoryEmojis = mapOf(
                                    "All" to "💬 All",
                                    "শুভেচ্ছা" to "👋 শুভেচ্ছা",
                                    "অর্ডার" to "📦 অর্ডার",
                                    "ডেলিভারি" to "🚚 ডেলিভারি",
                                    "পেমেন্ট" to "💳 পেমেন্ট",
                                    "সহায়তা" to "🛠️ সহায়তা"
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    items(categories) { categoryName ->
                                        val isSelected = cat == categoryName
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCategory.value = categoryName },
                                            label = { Text(categoryEmojis[categoryName] ?: categoryName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Filter and Search the local list
                                val filteredReplies = repliesList.filter { item ->
                                    val matchesCat = cat == "All" || item.category.contains(cat) || cat.contains(item.category)
                                    val matchesQuery = query.isBlank() ||
                                            item.title.contains(query, ignoreCase = true) ||
                                            item.content.contains(query, ignoreCase = true)
                                    matchesCat && matchesQuery
                                }

                                if (filteredReplies.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "কোনো রিপ্লাই পাওয়া যায়নি",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filteredReplies) { reply ->
                                            val catColor = when (reply.category) {
                                                "শুভেচ্ছা" -> Color(0xFF818CF8) // Indigo
                                                "অর্ডার" -> Color(0xFFF59E0B)  // Amber
                                                "ডেলিভারি" -> Color(0xFF10B981) // Emerald
                                                "পেমেন্ট" -> Color(0xFFEC4899)   // Pink
                                                "সহায়তা" -> Color(0xFF06B6D4)   // Cyan
                                                else -> MaterialTheme.colorScheme.primary
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        // Copy to Clipboard
                                                        copyToClipboard(reply.content)
                                                        Toast.makeText(context, "কপি করা হয়েছে: ${reply.title}", Toast.LENGTH_SHORT).show()
                                                        // Increment DB use count
                                                        serviceScope.launch {
                                                            repository.incrementUseCount(reply.id)
                                                        }
                                                        // Minimize
                                                        toggleExpanded()
                                                    },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = CardDefaults.outlinedCardBorder()
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                                    // Left vertical color line indicating category
                                                    Box(
                                                        modifier = Modifier
                                                            .width(5.dp)
                                                            .fillMaxHeight()
                                                            .background(catColor)
                                                    )

                                                    Column(modifier = Modifier.padding(10.dp).weight(1f)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = reply.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = categoryEmojis[reply.category] ?: reply.category,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = catColor,
                                                                modifier = Modifier
                                                                    .background(catColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = reply.content,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // AI Assist Tab
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "কাস্টমারের মেসেজের ওপর ভিত্তি করে AI দিয়ে প্রফেশনাল রিপ্লাই তৈরি করুন:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = prompt,
                                    onValueChange = { aiPrompt.value = it },
                                    placeholder = { Text("যেমন: কাস্টমার সাইজ চার্ট চাচ্ছে অথবা প্রোডাক্টটির দাম কত জানতে চাচ্ছে...", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(75.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    maxLines = 3,
                                    textStyle = TextStyle(fontSize = 12.sp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        if (prompt.isBlank()) {
                                            Toast.makeText(context, "অনুগ্রহ করে একটি প্রম্পট লিখুন", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isGeneratingState.value = true
                                        serviceScope.launch {
                                            val generated = GeminiService.generateReply(prompt)
                                            aiResponse.value = generated
                                            isGeneratingState.value = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isGenerating,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    if (isGenerating) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("তৈরি হচ্ছে...", fontSize = 13.sp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI রিপ্লাই তৈরি করুন", fontSize = 13.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // AI Response View
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                        if (response.isEmpty() && !isGenerating) {
                                            Text(
                                                text = "এখানে আপনার এআই জেনারেটেড রিপ্লাই দেখা যাবে...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        } else {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f)
                                                        .verticalScroll(rememberScrollState())
                                                ) {
                                                    Text(
                                                        text = response,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                if (response.isNotEmpty() && !response.startsWith("Error")) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceEvenly
                                                    ) {
                                                        ElevatedButton(
                                                            onClick = {
                                                                copyToClipboard(response)
                                                                Toast.makeText(context, "Copied AI Reply", Toast.LENGTH_SHORT).show()
                                                                toggleExpanded()
                                                            },
                                                            modifier = Modifier.height(32.dp).weight(1f).padding(end = 4.dp),
                                                            contentPadding = PaddingValues(0.dp)
                                                        ) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("কপি করুন", fontSize = 11.sp)
                                                        }

                                                        ElevatedButton(
                                                            onClick = {
                                                                // Quickly Save this response
                                                                serviceScope.launch {
                                                                    val count = repliesList.size + 1
                                                                    val newReply = SavedReply(
                                                                        title = "এআই রিপ্লাই $count (AI Reply)",
                                                                        content = response,
                                                                        category = "সহায়তা"
                                                                    )
                                                                    repository.insertReply(newReply)
                                                                    Toast.makeText(context, "Saved to Quick Replies!", Toast.LENGTH_SHORT).show()
                                                                    activeTab = 0 // Switch back
                                                                }
                                                            },
                                                            modifier = Modifier.height(32.dp).weight(1f).padding(start = 4.dp),
                                                            contentPadding = PaddingValues(0.dp)
                                                        ) {
                                                            Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("সংরক্ষণ করুন", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("QuickReply", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroy() {
        isRunning = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        super.onDestroy()
    }

    // Compose compatibility Boilerplate implementations
    private var savedStateRegistryOwner = object : SavedStateRegistryOwner {
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    }
}
