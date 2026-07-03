package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.GeminiService
import com.example.data.SavedReply
import com.example.data.SavedReplyRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var repository: SavedReplyRepository
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = AppDatabase.getDatabase(applicationContext)
        repository = SavedReplyRepository(database.savedReplyDao())
        viewModel = MainViewModel(repository)

        // Prepopulate standard replies if database is empty on launch
        lifecycleScope.launch {
            repository.prepopulateIfEmpty()
        }

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger a recomposition in compose screens that observe permission states
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Saved Replies, 1: AI Assistant, 2: Control Center

    // Track state of permission and service running
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isServiceRunning by remember { mutableStateOf(false) }

    // Periodic checker to ensure states are fresh
    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
            isServiceRunning = QuickReplyService.isRunning
            delay(1000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(vertical = 18.dp, horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Poptic Reply",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceRunning) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServiceRunning) "ফ্লোটিং বাবল সচল রয়েছে" else "ফ্লোটিং বাবল বন্ধ আছে",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceRunning) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            selectedTab = 2 // Go to settings
                        },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.18f), CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Quickreply, contentDescription = "Saved Replies") },
                    label = { Text("রিপ্লাই সমূহ", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant") },
                    label = { Text("এআই রাইটার", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.SettingsSuggest, contentDescription = "Control Center") },
                    label = { Text("কন্ট্রোল প্যানেল", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> SavedRepliesTab(viewModel)
                1 -> AiAssistantTab(viewModel)
                2 -> ControlCenterTab(hasOverlayPermission, isServiceRunning, onPermissionCheck = {
                    hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(context)
                    } else {
                        true
                    }
                })
            }
        }
    }
}

@Composable
fun SavedRepliesTab(viewModel: MainViewModel) {
    val replies by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val selectedCat by viewModel.selectedCategory.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingReply by remember { mutableStateOf<SavedReply?>(null) }

    val context = LocalContext.current
    val categories = listOf("All", "শুভেচ্ছা", "অর্ডার", "ডেলিভারি", "পেমেন্ট", "সহায়তা")
    val categoryEmojis = mapOf(
        "All" to "💬 All",
        "শুভেচ্ছা" to "👋 শুভেচ্ছা",
        "অর্ডার" to "📦 অর্ডার",
        "ডেলিভারি" to "🚚 ডেলিভারি",
        "পেমেন্ট" to "💳 পেমেন্ট",
        "সহায়তা" to "🛠️ সহায়তা"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Welcome Header
        Text(
            text = "আপনার সংরক্ষিত কুইক রিপ্লাই সমূহ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "গ্রাহকদের চটজলদি উত্তর দিতে এখান থেকে এক ট্যাপে কপি করতে পারেন অথবা ভাসমান বাবলটি ব্যবহার করুন।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("টাইটেল অথবা কন্টেন্ট দিয়ে সার্চ করুন...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Horizontal Category Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            items(categories) { category ->
                val isSelected = selectedCat == category
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectCategory(category) },
                    label = { Text(categoryEmojis[category] ?: category, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (replies.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Empty",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "কোনো সংরক্ষিত রিপ্লাই খুঁজে পাওয়া যায়নি",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "নিচের (+) বাটনে চাপ দিয়ে আপনার প্রথম কুইক রিপ্লাই তৈরি করুন!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Replies list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(replies) { reply ->
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
                                // Copy content to clipboard
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("QuickReply", reply.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "কপি করা হয়েছে: ${reply.title}", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            // Left Category color vertical indicator
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .fillMaxHeight()
                                    .background(catColor)
                            )

                            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = reply.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = categoryEmojis[reply.category] ?: reply.category,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = catColor,
                                        modifier = Modifier
                                            .background(catColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = reply.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = "Usage",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${reply.useCount} বার কপি হয়েছে",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { editingReply = reply },
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
                                                .size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteReply(reply) },
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), CircleShape)
                                                .size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Floating Action Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_reply_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Quick Reply")
            }
        }
    }

    // Add Dialog
    if (showAddDialog) {
        ReplyFormDialog(
            title = "নতুন কুইক রিপ্লাই যোগ করুন",
            onDismiss = { showAddDialog = false },
            onConfirm = { title, content, category ->
                viewModel.addReply(SavedReply(title = title, content = content, category = category))
                showAddDialog = false
            }
        )
    }

    // Edit Dialog
    editingReply?.let { reply ->
        ReplyFormDialog(
            title = "রিপ্লাই পরিবর্তন করুন",
            initialTitle = reply.title,
            initialContent = reply.content,
            initialCategory = reply.category,
            onDismiss = { editingReply = null },
            onConfirm = { title, content, category ->
                viewModel.updateReply(reply.copy(title = title, content = content, category = category))
                editingReply = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyFormDialog(
    title: String,
    initialTitle: String = "",
    initialContent: String = "",
    initialCategory: String = "শুভেচ্ছা",
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var formTitle by remember { mutableStateOf(initialTitle) }
    var formContent by remember { mutableStateOf(initialContent) }
    var formCategory by remember { mutableStateOf(initialCategory) }

    val categories = listOf("শুভেচ্ছা", "অর্ডার", "ডেলিভারি", "পেমেন্ট", "সহায়তা")
    var isDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = formTitle,
                    onValueChange = { formTitle = it },
                    label = { Text("টাইটেল (যেমন: অর্ডার নিয়ম)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = formContent,
                    onValueChange = { formContent = it },
                    label = { Text("সংরক্ষিত উত্তর বা কন্টেন্ট") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 6
                )

                // Category dropdown selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = formCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ক্যাটাগরি") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            categories.forEach { categoryName ->
                                DropdownMenuItem(
                                    text = { Text(categoryName) },
                                    onClick = {
                                        formCategory = categoryName
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (formTitle.isNotBlank() && formContent.isNotBlank()) {
                        onConfirm(formTitle, formContent, formCategory)
                    }
                },
                enabled = formTitle.isNotBlank() && formContent.isNotBlank()
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun AiAssistantTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var customerMessage by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedReply by remember { mutableStateOf("") }

    var selectedTone by remember { mutableStateOf("নম্র (Polite)") }
    var isToneExpanded by remember { mutableStateOf(false) }
    val tones = listOf("নম্র (Polite)", "সংক্ষিপ্ত (Concise)", "পেশাদার (Professional)", "উচ্ছ্বসিত (Excited)")

    var showSaveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Welcome Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Icon",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "স্মার্ট এআই রিপ্লাই রাইটার",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "কাস্টমারের মেসেজ বা সংকেত লিখুন, আমাদের Gemini AI আপনার ব্যবসার জন্য চমৎকার একটি প্রফেশনাল উত্তর লিখে দেবে!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Section Inputs
        Text(
            text = "কাস্টমারের মেসেজ বা প্রশ্ন (ঐচ্ছিক)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = customerMessage,
            onValueChange = { customerMessage = it },
            placeholder = { Text("কাস্টমার যা পাঠিয়েছেন, যেমন: 'ভাই প্রোডাক্টটি অর্ডার করলে কতদিনে পাব?'") },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(8.dp),
            maxLines = 3
        )

        Text(
            text = "আপনি কী উত্তর দিতে চান? (সংক্ষেপে লিখুন)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            placeholder = { Text("যেমন: '২ দিনে পাবেন, ডেলিভারি চার্জ ৮০ টাকা ঢাকার ভেতর'") },
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(8.dp),
            maxLines = 3
        )

        // Tone Selection Dropdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "রিপ্লাইয়ের সুর (Tone):",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Box {
                Button(
                    onClick = { isToneExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(selectedTone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                }

                DropdownMenu(
                    expanded = isToneExpanded,
                    onDismissRequest = { isToneExpanded = false }
                ) {
                    tones.forEach { toneName ->
                        DropdownMenuItem(
                            text = { Text(toneName, fontSize = 13.sp) },
                            onClick = {
                                selectedTone = toneName
                                isToneExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Generate Button
        Button(
            onClick = {
                if (prompt.isBlank() && customerMessage.isBlank()) {
                    Toast.makeText(context, "দয়া করে যেকোনো একটি ঘর পূরণ করুন", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isGenerating = true
                coroutineScope.launch {
                    val toneInstruction = "Make the response tone $selectedTone."
                    val fullPrompt = if (prompt.isNotEmpty()) "$prompt. $toneInstruction" else "Create a reply to this customer message. $toneInstruction"
                    val response = GeminiService.generateReply(fullPrompt, customerMessage)
                    generatedReply = response
                    isGenerating = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("generate_button"),
            enabled = !isGenerating,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("এআই রিপ্লাই লিখছে...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Write")
                Spacer(modifier = Modifier.width(8.dp))
                Text("রিপ্লাই তৈরি করুন", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AI Generated Result view
        AnimatedVisibility(
            visible = generatedReply.isNotEmpty() || isGenerating,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "এআই ড্রাফট (Generated Draft):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("QuickReply", generatedReply)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = generatedReply.isNotEmpty()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isGenerating) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(
                            text = generatedReply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!generatedReply.startsWith("Error")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("QuickReply", generatedReply)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("কপি করুন")
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Button(
                                    onClick = { showSaveDialog = true }
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("রিপ্লাই সেভ করুন")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        ReplyFormDialog(
            title = "এআই রিপ্লাইটি কুইক লিস্টে সেভ করুন",
            initialTitle = "এআই কাস্টম রিপ্লাই (AI Quick Reply)",
            initialContent = generatedReply,
            initialCategory = "সহায়তা",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, content, category ->
                viewModel.addReply(SavedReply(title = title, content = content, category = category))
                showSaveDialog = false
                Toast.makeText(context, "কুইক লিস্টে সেভ করা হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ControlCenterTab(
    hasOverlayPermission: Boolean,
    isServiceRunning: Boolean,
    onPermissionCheck: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "ওভারলে এবং বাবল কন্ট্রোল প্যানেল",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "অন্যান্য অ্যাপের ওপর (যেমন মেসেঞ্জার, হোয়াটসঅ্যাপ) রিপ্লাই বাবল প্রদর্শন করার জন্য নিচের অপশনগুলো কনফিগার করুন।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. Permission status card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasOverlayPermission)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (hasOverlayPermission) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (hasOverlayPermission) "ওভারলে পারমিশন দেওয়া আছে" else "ওভারলে পারমিশন প্রয়োজন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasOverlayPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (hasOverlayPermission) "অ্যাপটি অন্যান্য উইন্ডোর ওপরে বাবল প্রদর্শন করতে প্রস্তুত।" else "অন্যান্য অ্যাপের ওপর বাবল দেখাতে ডিসপ্লে ওভার পারমিশন প্রয়োজন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasOverlayPermission) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("পারমিশন চালু করুন (Grant Permission)")
                    }
                }
            }
        }

        // 2. Start/Stop Service Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ফ্লোটিং রিপ্লাই বাবল (Floating Bubble)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "এটি চালু করলে আপনার স্ক্রিনে একটি ছোট গোল বাবল ভাসমান থাকবে। যাতে ট্যাপ করে কাস্টমারকে চটজলদি উত্তর দিতে পারেন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { checked ->
                            if (!hasOverlayPermission && checked) {
                                Toast.makeText(context, "দয়া করে আগে ওভারলে পারমিশন দিন!", Toast.LENGTH_LONG).show()
                                return@Switch
                            }

                            val serviceIntent = Intent(context, QuickReplyService::class.java)
                            if (checked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                                Toast.makeText(context, "ফ্লোটিং বাবল সচল করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            } else {
                                context.stopService(serviceIntent)
                                Toast.makeText(context, "ফ্লোটিং বাবল বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // 3. Instruction steps
        Text(
            text = "কীভাবে ব্যবহার করবেন (How to Use):",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        val steps = listOf(
            StepItem(
                number = "১",
                title = "ডিসপ্লে ওভার পারমিশন দিন",
                desc = "প্রথমে কন্ট্রোল প্যানেলে থাকা লাল ওয়ার্নিং কার্ড থেকে পারমিশন চালু করুন।"
            ),
            StepItem(
                number = "২",
                title = "ফ্লোটিং বাবল অন করুন",
                desc = "উপরের ফ্লোটিং রিপ্লাই বাবল অপশনটি চালু বা অন (Switch On) করুন।"
            ),
            StepItem(
                number = "৩",
                title = "যেকোনো অ্যাপ ওপেন করুন",
                desc = "হোয়াটসঅ্যাপ, মেসেঞ্জার বা ফেসবুক পেজ ইনবক্স ওপেন করুন। বাবলটি স্ক্রিনের একপাশে ভাসমান দেখতে পাবেন।"
            ),
            StepItem(
                number = "৪",
                title = "বাবলে ট্যাপ করুন ও কপি করুন",
                desc = "বাবলে ট্যাপ করে আপনার সংরক্ষিত কুইক রিপ্লাই খুঁজে বের করুন এবং ট্যাপ করে কপি করে ইনবক্সে পেস্ট করে সেন্ড করুন।"
            ),
            StepItem(
                number = "৫",
                title = "এআই সাহায্য নিয়ে উত্তর দিন",
                desc = "বাবলের ভেতরের 'AI' অপশন ব্যবহার করে যেকোনো কঠিন বা নতুন প্রশ্নের সুন্দর বাংলা উত্তর বানিয়ে নিতে পারেন নিমেষেই!"
            )
        )

        steps.forEach { step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = step.number,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = step.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

data class StepItem(
    val number: String,
    val title: String,
    val desc: String
)
