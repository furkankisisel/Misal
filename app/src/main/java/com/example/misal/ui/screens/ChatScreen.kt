package com.example.misal.ui.screens

import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Poll
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.misal.ui.utils.AudioPlayerHelper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import com.example.misal.ui.utils.AudioRecorderHelper

import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.misal.domain.model.Chat
import com.example.misal.domain.model.Message
import com.example.misal.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: Chat, 
    onBack: () -> Unit,
    onContactProfileClick: (String) -> Unit,
    onCallClick: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(chat.id) {
        viewModel.loadMessages(chat.id, chat.contactId)
    }

    DisposableEffect(chat.id) {
        onDispose {
            viewModel.clearChat()
        }
    }

    val messages by viewModel.messages.collectAsState()
    val isOnline by viewModel.isContactOnline.collectAsState()
    val lastSeen by viewModel.contactLastSeen.collectAsState()
    val isTyping by viewModel.isContactTyping.collectAsState()

    val currentChat by viewModel.currentChat.collectAsState()
    val pinnedMessageId = currentChat?.pinnedMessageId
    val pinnedMessage = messages.find { it.id == pinnedMessageId }

    LaunchedEffect(messages) {
        viewModel.markMessagesAsRead()
    }

    androidx.activity.compose.BackHandler {
        onBack()
    }

    val error by viewModel.error.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    val replyingToMessage by viewModel.replyingToMessage.collectAsState()
    var messageToForward by remember { mutableStateOf<Message?>(null) }
    var messageToShowReadBy by remember { mutableStateOf<Message?>(null) }
    val readByNames by viewModel.readByNames.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        androidx.compose.material3.TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Mesajlarda ara...", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) },
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                cursorColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.clickable { onContactProfileClick(chat.contactId) },
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            // Profil resmi
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                val profileBitmap = remember(chat.contactProfilePictureBase64) {
                                    if (chat.contactProfilePictureBase64 != null) {
                                        try {
                                            val bytes = android.util.Base64.decode(chat.contactProfilePictureBase64, android.util.Base64.NO_WRAP)
                                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        } catch (e: Exception) { null }
                                    } else null
                                }
                                if (profileBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = profileBitmap.asImageBitmap(),
                                        contentDescription = "Profil Resmi",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = chat.contactName.firstOrNull()?.uppercase() ?: "?",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(if (chat.isGroup) chat.groupName ?: "Grup" else chat.contactName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                val statusText = when {
                                    isTyping -> "Yazıyor..."
                                    isOnline -> "Çevrimiçi"
                                    lastSeen != null -> "Son görülme: ${formatter.format(Date(lastSeen!!))}"
                                    else -> ""
                                }
                                if (statusText.isNotEmpty()) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Ara", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Temizle", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    if (!isSearching) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Daha Fazla", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    if (!chat.isGroup) {
                        IconButton(onClick = { onCallClick("audio") }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Call, contentDescription = "Sesli Ara", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { onCallClick("video") }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Videocam, contentDescription = "Görüntülü Ara", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Süreli Mesajlar") },
                            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showTimerDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { 
                    inputText = it
                    if (it.isNotEmpty()) {
                        viewModel.onUserTyping()
                    }
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        if (editingMessage != null) {
                            viewModel.editMessage(editingMessage!!.id, inputText)
                            editingMessage = null
                        } else {
                            viewModel.sendMessage(inputText)
                        }
                        inputText = ""
                    }
                },
                onMediaSelected = { uri, isViewOnce ->
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        viewModel.sendMedia(bytes, if (isViewOnce) "VIEW_ONCE_IMAGE" else "IMAGE")
                    }
                },
                onAudioRecorded = { bytes -> viewModel.sendAudio(bytes) },
                onPollRequested = { pollData -> viewModel.sendPoll(pollData) },
                onLocationRequested = { lat, lng -> viewModel.sendLocation(lat, lng) },
                onCameraCaptured = { uri, isViewOnce ->
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        viewModel.sendMedia(bytes, if (isViewOnce) "VIEW_ONCE_IMAGE" else "IMAGE")
                    }
                },
                editingMessageText = editingMessage?.text,
                onCancelEdit = {
                    editingMessage = null
                    inputText = ""
                },
                replyingToMessage = replyingToMessage,
                onCancelReply = { viewModel.setReplyingToMessage(null) }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (showTimerDialog) {
                AlertDialog(
                    onDismissRequest = { showTimerDialog = false },
                    title = { Text("Süreli Mesajlar") },
                    text = {
                        Column {
                            Text("Yeni mesajlar ne kadar süre sonra kaybolsun?")
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val options = listOf(
                                "Kapalı" to null,
                                "24 Saat" to 24 * 60 * 60 * 1000L,
                                "7 Gün" to 7 * 24 * 60 * 60 * 1000L
                            )
                            options.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setDisappearingTimer(value)
                                            showTimerDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentChat?.disappearingTimer == value,
                                        onClick = {
                                            viewModel.setDisappearingTimer(value)
                                            showTimerDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTimerDialog = false }) {
                            Text("Kapat")
                        }
                    }
                )
            }
            
            if (messageToForward != null) {
                var selectedChats by remember { mutableStateOf(setOf<String>()) }
                AlertDialog(
                    onDismissRequest = { messageToForward = null },
                    title = { Text("Mesajı İlet") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(allChats.filter { it.id != chat.id }) { chatItem ->
                                val isSelected = selectedChats.contains(chatItem.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                selectedChats = selectedChats - chatItem.id
                                            } else {
                                                selectedChats = selectedChats + chatItem.id
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (chatItem.isGroup) chatItem.groupName ?: "Grup" else chatItem.contactName ?: "Kişi")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (selectedChats.isNotEmpty()) {
                                    viewModel.forwardMessage(messageToForward!!, selectedChats.toList())
                                    messageToForward = null
                                    android.widget.Toast.makeText(context, "Mesaj iletildi", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = selectedChats.isNotEmpty()
                        ) {
                            Text("İlet")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { messageToForward = null }) {
                            Text("İptal")
                        }
                    }
                )
            }
            
            LaunchedEffect(messageToShowReadBy) {
                if (messageToShowReadBy != null) {
                    viewModel.fetchReadByNames(messageToShowReadBy!!.readBy)
                }
            }

            if (messageToShowReadBy != null) {
                AlertDialog(
                    onDismissRequest = { messageToShowReadBy = null },
                    title = { Text("Kimler Okudu") },
                    text = {
                        if (readByNames.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                items(readByNames) { name ->
                                    Text(name, modifier = Modifier.padding(8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { messageToShowReadBy = null }) {
                            Text("Kapat")
                        }
                    }
                )
            }
            
            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (pinnedMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("📌 Sabitlenmiş Mesaj", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(com.example.misal.ui.utils.formatRichText(pinnedMessage.text), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.unpinMessage() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Kaldır")
                            }
                        }
                        HorizontalDivider()
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(16.dp),
                            reverseLayout = true
                        ) {
                            items(
                                if (isSearching && searchQuery.isNotEmpty()) {
                                    messages.reversed().filter { it.text.contains(searchQuery, ignoreCase = true) }
                                } else {
                                    messages.reversed()
                                }
                            ) { message ->
                                MessageBubble(
                                    message = message,
                                    onEdit = {
                                        editingMessage = it
                                        inputText = it.text
                                    },
                                    onDelete = {
                                        viewModel.deleteMessage(it.id)
                                    },
                                    onDeleteForMe = {
                                        viewModel.deleteMessageForMe(it.id)
                                    },
                                    onReact = { emoji ->
                                        viewModel.reactToMessage(message.id, emoji)
                                    },
                                    onPin = {
                                        viewModel.pinMessage(message.id)
                                    },
                                    onReply = {
                                        viewModel.setReplyingToMessage(it)
                                    },
                                    onForward = {
                                        messageToForward = it
                                    },
                                    onShowReadBy = {
                                        messageToShowReadBy = it
                                    },
                                    onVoteRequested = { optionIndex ->
                                        viewModel.votePoll(message.id, optionIndex)
                                    },
                                    repliedMessage = message.replyToMessageId?.let { replyId -> messages.find { it.id == replyId } },
                                    currentUserId = currentUserId ?: ""
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message, 
    onEdit: (Message) -> Unit = {}, 
    onDelete: (Message) -> Unit = {},
    onDeleteForMe: (Message) -> Unit = {},
    onReact: (String) -> Unit = {},
    onPin: () -> Unit = {},
    onReply: (Message) -> Unit = {},
    onForward: (Message) -> Unit = {},
    onShowReadBy: (Message) -> Unit = {},
    onVoteRequested: (Int) -> Unit = {},
    repliedMessage: Message? = null,
    currentUserId: String = ""
) {
    val isFromMe = message.isFromMe
    val backgroundColor = if (isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Mesaj Seçenekleri") },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "👏")
                        emojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable {
                                        showDialog = false
                                        onReact(emoji)
                                    },
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = {
                        showDialog = false
                        onReply(message)
                    }) {
                        Text("Yanıtla", color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = {
                        showDialog = false
                        onForward(message)
                    }) {
                        Text("İlet", color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (isFromMe && message.readBy.isNotEmpty()) {
                        TextButton(onClick = {
                            showDialog = false
                            onShowReadBy(message)
                        }) {
                            Text("Kimler Okudu (${message.readBy.size})", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    TextButton(onClick = {
                        showDialog = false
                        onPin()
                    }) {
                        Text("Sabitle", color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (isFromMe && message.messageType == "TEXT") {
                        TextButton(onClick = {
                            showDialog = false
                            onEdit(message)
                        }) {
                            Text("Düzenle", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    TextButton(onClick = {
                        showDialog = false
                        onDeleteForMe(message)
                    }) {
                        Text("Benden Sil", color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (isFromMe) {
                        TextButton(onClick = {
                            showDialog = false
                            onDelete(message)
                        }) {
                            Text("Herkesten Sil", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(color = backgroundColor, shape = shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (!message.isDeleted) {
                            showDialog = true
                        }
                    }
                )
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Column {
                if (repliedMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = if (repliedMessage.isFromMe) "Siz" else "Karşı Taraf",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = repliedMessage.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                if (message.isDeleted) {
                    Text(
                        text = "🚫 Bu mesaj silindi",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    )
                } else if (message.messageType == "VIEW_ONCE_IMAGE") {
                    var showViewOnce by remember { mutableStateOf(false) }
                    
                    if (showViewOnce) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { 
                                showViewOnce = false 
                                if (!isFromMe) {
                                    onDelete(message) 
                                }
                            },
                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                if (message.localMediaPath != null) {
                                    coil.compose.AsyncImage(
                                        model = java.io.File(message.localMediaPath),
                                        contentDescription = "Tek Gösterimlik Fotoğraf",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                } else {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center))
                                }
                                IconButton(
                                    onClick = {
                                        showViewOnce = false
                                        if (!isFromMe) {
                                            onDelete(message)
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = Color.White)
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                if (!isFromMe) {
                                    showViewOnce = true
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = "Tek Gösterimlik", tint = textColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isFromMe) "Fotoğraf (Tek Gösterimlik)" else "Fotoğrafı Gör (Tek Gösterimlik)", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (message.messageType == "IMAGE") {
                    if (message.localMediaPath != null) {
                        coil.compose.AsyncImage(
                            model = java.io.File(message.localMediaPath),
                            contentDescription = "Fotoğraf",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(bottom = 4.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (message.mediaUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.Gray.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (message.messageType == "AUDIO") {
                    val audioPlayer = remember { AudioPlayerHelper() }
                    var isPlaying by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    audioPlayer.stopAudio()
                                    isPlaying = false
                                } else {
                                    if (message.localMediaPath != null) {
                                        isPlaying = true
                                        audioPlayer.playAudio(java.io.File(message.localMediaPath)) {
                                            isPlaying = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Durdur" else "Oynat",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (message.localMediaPath == null) "İndiriliyor..." else "Sesli Mesaj", color = textColor)
                    }
                } else if (message.messageType == "LOCATION") {
                    val locationParts = message.text.split(",")
                    if (locationParts.size == 2) {
                        val lat = locationParts[0]
                        val lng = locationParts[1]
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.LocationOn, contentDescription = "Konum", tint = textColor, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Konum: $lat, $lng", color = textColor, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            val context = androidx.compose.ui.platform.LocalContext.current
                            androidx.compose.material3.Button(onClick = {
                                try {
                                    val uri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                                    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                    context.startActivity(mapIntent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    android.widget.Toast.makeText(context, "Harita uygulaması bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Haritada Aç")
                            }
                        }
                    } else {
                        Text(text = "🚫 Konum çözülemedi", color = textColor, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (message.messageType == "POLL") {
                    val pollData = remember(message.text) {
                        try {
                            val pollJson = org.json.JSONObject(message.text)
                            val question = pollJson.getString("question")
                            val options = pollJson.getJSONArray("options").let { arr ->
                                List(arr.length()) { arr.getString(it) }
                            }
                            question to options
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (pollData != null) {
                        val (question, options) = pollData
                        val totalVotes = message.pollVotes.size
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text("📊 $question", style = MaterialTheme.typography.titleMedium, color = textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            options.forEachIndexed { index, option ->
                                val votesForOption = message.pollVotes.values.count { it == index }
                                val percentage = if (totalVotes > 0) (votesForOption.toFloat() / totalVotes * 100).toInt() else 0
                                val isMyVote = message.pollVotes[currentUserId] == index
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onVoteRequested(index) }
                                        .background(
                                            if (isMyVote) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option,
                                        color = if (isMyVote) MaterialTheme.colorScheme.onPrimaryContainer else textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$percentage%",
                                        color = if (isMyVote) MaterialTheme.colorScheme.onPrimaryContainer else textColor,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { percentage / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text("$totalVotes oy", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
                        }
                    } else {
                        Text("Anket yüklenemedi", color = textColor)
                    }
                } else {
                    Text(
                        text = com.example.misal.ui.utils.formatRichText(message.text),
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeString = if (message.timestamp > 0) formatter.format(Date(message.timestamp)) else ""
                
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited && !message.isDeleted) {
                        Text(
                            text = "(düzenlendi)",
                            color = textColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = timeString,
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    
                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val iconVector = when {
                            message.isRead -> Icons.Filled.DoneAll
                            message.isDelivered -> Icons.Filled.DoneAll
                            else -> Icons.Filled.Check
                        }
                        
                        val iconColor = when {
                            message.isRead -> Color(0xFF4FC3F7) // Mavi
                            else -> textColor.copy(alpha = 0.7f) // Gri/Beyaz (Tema rengi)
                        }
                        
                        val iconDesc = when {
                            message.isRead -> "Okundu"
                            message.isDelivered -> "İletildi"
                            else -> "Gönderildi"
                        }
                        
                        Icon(
                            imageVector = iconVector,
                            contentDescription = iconDesc,
                            tint = iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        message.reactions.values.distinct().forEach { emoji ->
                            val count = message.reactions.values.count { it == emoji }
                            Text(text = if (count > 1) "$emoji $count" else emoji, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    text: String, 
    onTextChange: (String) -> Unit, 
    onSend: () -> Unit, 
    onMediaSelected: (android.net.Uri, Boolean) -> Unit,
    onAudioRecorded: (ByteArray) -> Unit,
    onPollRequested: (com.example.misal.domain.model.PollData) -> Unit,
    onLocationRequested: (Double, Double) -> Unit,
    onCameraCaptured: (android.net.Uri, Boolean) -> Unit,
    editingMessageText: String? = null,
    onCancelEdit: () -> Unit = {},
    replyingToMessage: com.example.misal.domain.model.Message? = null,
    onCancelReply: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioRecorder = remember { AudioRecorderHelper(context) }
    var isRecording by remember { mutableStateOf(false) }
    val recordAudioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            isRecording = true
            audioRecorder.startRecording()
        }
    }

    var isViewOnceMode by remember { mutableStateOf(false) }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { onMediaSelected(it, isViewOnceMode) }
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    
    val cameraUri = remember {
        val file = java.io.File(context.cacheDir, "camera_capture.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            onCameraCaptured(cameraUri, isViewOnceMode)

        }
    }
    
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(cameraUri)
        } else {
            android.widget.Toast.makeText(context, "Kamera izni gerekli", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                    if (location != null) {
                        if (location.latitude == 0.0 && location.longitude == 0.0) {
                            android.widget.Toast.makeText(context, "Cihazınız (veya emülatör) 0.0, 0.0 konumunu döndürüyor. Lütfen cihaz/emülatör ayarlarından geçerli bir konum belirleyin.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            onLocationRequested(location.latitude, location.longitude)
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Konum alınamadı, cihazın konum servisinin açık olduğundan emin olun.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: SecurityException) {
                // Ignore
            }
        } else {
            android.widget.Toast.makeText(context, "Konum izni gerekli", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column {
            if (editingMessageText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mesaj Düzenleniyor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(editingMessageText, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onCancelEdit) {
                        Icon(Icons.Filled.Close, contentDescription = "İptal")
                    }
                }
            } else if (replyingToMessage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Yanıtlanıyor: ${if (replyingToMessage.isFromMe) "Siz" else "Karşı Taraf"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(replyingToMessage.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Filled.Close, contentDescription = "İptal")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showBottomSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Ek Seçenekler",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Transparent),
                    placeholder = { Text("Mesaj yazın...") },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (text.isNotBlank() || editingMessageText != null) {
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(if (isRecording) Color.Red else MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(8.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                            isRecording = true
                                            audioRecorder.startRecording()
                                            tryAwaitRelease()
                                            isRecording = false
                                            val audioBytes = audioRecorder.stopRecording()
                                            if (audioBytes != null) {
                                                onAudioRecorded(audioBytes)
                                            }
                                        } else {
                                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Ses Kaydet",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        androidx.compose.material3.ExperimentalMaterial3Api::class.java // Just to satisfy opt-in if needed
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tek Gösterimlik (Kamera / Galeri)")
                    androidx.compose.material3.Switch(
                        checked = isViewOnceMode,
                        onCheckedChange = { isViewOnceMode = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BottomSheetItem(
                        icon = Icons.Filled.CameraAlt,
                        label = "Kamera",
                        onClick = {
                            showBottomSheet = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                    BottomSheetItem(
                        icon = Icons.Filled.Image,
                        label = "Galeri",
                        onClick = {
                            showBottomSheet = false
                            photoPickerLauncher.launch("image/*")
                        }
                    )
                    BottomSheetItem(
                        icon = Icons.Filled.Poll,
                        label = "Anket",
                        onClick = {
                            showBottomSheet = false
                            showPollDialog = true
                        }
                    )
                    BottomSheetItem(
                        icon = Icons.Filled.LocationOn,
                        label = "Konum",
                        onClick = {
                            showBottomSheet = false
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showPollDialog) {
        var question by remember { mutableStateOf("") }
        var options by remember { mutableStateOf(listOf("", "")) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Anket Oluştur") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Soru") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    options.forEachIndexed { index, option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedTextField(
                                value = option,
                                onValueChange = { newText ->
                                    val newOptions = options.toMutableList()
                                    newOptions[index] = newText
                                    options = newOptions
                                },
                                label = { Text("Seçenek ${index + 1}") },
                                modifier = Modifier.weight(1f)
                            )
                            if (options.size > 2) {
                                IconButton(onClick = {
                                    val newOptions = options.toMutableList()
                                    newOptions.removeAt(index)
                                    options = newOptions
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Sil")
                                }
                            }
                        }
                    }
                    if (options.size < 10) {
                        TextButton(onClick = {
                            val newOptions = options.toMutableList()
                            newOptions.add("")
                            options = newOptions
                        }) {
                            Text("Seçenek Ekle")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val validOptions = options.filter { it.isNotBlank() }
                        if (question.isNotBlank() && validOptions.size >= 2) {
                            onPollRequested(com.example.misal.domain.model.PollData(question, validOptions))
                            showPollDialog = false
                        }
                    }
                ) {
                    Text("Oluştur")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPollDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun BottomSheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
