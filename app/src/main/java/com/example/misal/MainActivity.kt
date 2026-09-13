package com.example.misal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.misal.domain.model.Chat
import com.example.misal.ui.screens.AddContactScreen
import com.example.misal.ui.screens.ChatListScreen
import com.example.misal.ui.screens.ChatScreen
import com.example.misal.ui.screens.ContactProfileScreen
import com.example.misal.ui.screens.LoginScreen
import com.example.misal.ui.screens.ProfileScreen
import com.example.misal.ui.screens.RegisterScreen
import com.example.misal.ui.screens.CreateGroupScreen
import com.example.misal.ui.screens.CallHistoryScreen
import com.example.misal.ui.theme.MisalTheme
import com.example.misal.ui.viewmodel.AuthViewModel
import com.example.misal.ui.viewmodel.CallViewModel
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.foundation.layout.padding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO), 101)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO), 101)
        }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    firestore.collection("users").document(userId).update("fcmToken", token)
                }
            }
        }
        setContent {
            MisalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MisalApp(intent = intent)
                }
            }
        }
    }
}

enum class Screen {
    Login, Register, ChatList, CallHistory, Chat, AddContact, Profile, ContactProfile, GroupProfile, CreateGroup
}

@Composable
fun MisalApp(
    authViewModel: AuthViewModel = hiltViewModel(), 
    callViewModel: CallViewModel = hiltViewModel(),
    intent: Intent? = null
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val incomingCall by callViewModel.incomingCall.collectAsState()
    val activeCall by callViewModel.activeCall.collectAsState()
    
    var currentScreen by remember { mutableStateOf(Screen.Login) }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var selectedContactId by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> authViewModel.updatePresence(true)
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> authViewModel.updatePresence(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(intent) {
        if (intent?.getBooleanExtra("incoming_call", false) == true) {
            val action = intent.getStringExtra("action")
            if (action == "ACCEPT_CALL" && incomingCall != null) {
                callViewModel.acceptCall(incomingCall!!)
            }
        }
    }

    // Otomatik Yönlendirme (Auth State Değiştiğinde)
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            currentScreen = Screen.ChatList
        } else {
            currentScreen = Screen.Login
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentScreen == Screen.ChatList || currentScreen == Screen.CallHistory) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Sohbetler") },
                        label = { Text("Sohbetler") },
                        selected = currentScreen == Screen.ChatList,
                        onClick = { currentScreen = Screen.ChatList }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Call, contentDescription = "Aramalar") },
                        label = { Text("Aramalar") },
                        selected = currentScreen == Screen.CallHistory,
                        onClick = { currentScreen = Screen.CallHistory }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentScreen) {
                Screen.Login -> {
                    LoginScreen(
                        onNavigateToRegister = { currentScreen = Screen.Register }
                    )
                }
                Screen.Register -> {
                    RegisterScreen(
                        onNavigateToLogin = { currentScreen = Screen.Login }
                    )
                }
                Screen.ChatList -> {
                    ChatListScreen(
                        onChatSelected = { chat ->
                            selectedChat = chat
                            currentScreen = Screen.Chat
                        },
                        onAddContact = {
                            currentScreen = Screen.AddContact
                        },
                        onProfileClick = {
                            currentScreen = Screen.Profile
                        },
                        onCreateGroup = {
                            currentScreen = Screen.CreateGroup
                        }
                    )
                }
                Screen.CallHistory -> {
                    CallHistoryScreen()
                }
            Screen.Chat -> {
                if (selectedChat != null) {
                    ChatScreen(
                        chat = selectedChat!!,
                        onBack = {
                            selectedChat = null
                            currentScreen = Screen.ChatList
                        },
                        onContactProfileClick = { contactId ->
                            if (selectedChat!!.isGroup) {
                                selectedContactId = selectedChat!!.id
                                currentScreen = Screen.GroupProfile
                            } else {
                                selectedContactId = contactId
                                currentScreen = Screen.ContactProfile
                            }
                        },
                        onCallClick = { type ->
                            // receiverId for 1-1 chat
                            val receiverId = selectedChat!!.contactId
                            callViewModel.startCall(receiverId, type)
                        }
                    )
                }
            }
            Screen.AddContact -> {
                AddContactScreen(
                    onBack = { currentScreen = Screen.ChatList },
                    onChatCreated = { chat ->
                        selectedChat = chat
                        currentScreen = Screen.Chat
                    }
                )
            }
            Screen.Profile -> {
                ProfileScreen(onBack = { currentScreen = Screen.ChatList })
            }
            Screen.ContactProfile -> {
                if (selectedContactId != null) {
                    ContactProfileScreen(
                        contactId = selectedContactId!!,
                        onBack = { currentScreen = Screen.Chat }
                    )
                }
            }
            Screen.GroupProfile -> {
                if (selectedContactId != null) {
                    com.example.misal.ui.screens.GroupProfileScreen(
                        chatId = selectedContactId!!,
                        onBack = { currentScreen = Screen.Chat }
                    )
                }
            }
            Screen.CreateGroup -> {
                com.example.misal.ui.screens.CreateGroupScreen(
                    onBack = { currentScreen = Screen.ChatList },
                    onGroupCreated = { currentScreen = Screen.ChatList }
                )
            }
        }

        // Call Overlays
        if (activeCall != null) {
            com.example.misal.ui.screens.ActiveCallScreen(
                callViewModel = callViewModel,
                call = activeCall!!,
                onEndCall = { /* ViewModel handles cleanup */ }
            )
        } else if (incomingCall != null) {
            com.example.misal.ui.screens.IncomingCallScreen(
                call = incomingCall!!,
                callerName = "Arayan Kişi", // Gerçekte name resolve edilmeli
                onAccept = { callViewModel.acceptCall(incomingCall!!) },
                onReject = { callViewModel.rejectCall(incomingCall!!.id) }
            )
        }
        }
    }
}
