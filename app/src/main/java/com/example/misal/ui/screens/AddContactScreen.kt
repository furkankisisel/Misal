package com.example.misal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.misal.domain.model.Chat
import com.example.misal.domain.model.User
import com.example.misal.ui.viewmodel.AddContactViewModel
import com.example.misal.ui.viewmodel.CreateChatState
import com.example.misal.ui.viewmodel.SearchState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    onBack: () -> Unit,
    onChatCreated: (Chat) -> Unit,
    viewModel: AddContactViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    val searchState by viewModel.searchState.collectAsState()
    val createChatState by viewModel.createChatState.collectAsState()

    LaunchedEffect(createChatState) {
        if (createChatState is CreateChatState.Success) {
            val state = createChatState as CreateChatState.Success
            onChatCreated(
                Chat(id = state.chatId, contactId = state.contactId, contactName = state.contactName, lastMessage = "", timestamp = System.currentTimeMillis())
            )
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kişi Ekle", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Kişinin E-posta Adresi") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.searchUser(email.trim()) }) {
                        Icon(Icons.Default.Search, contentDescription = "Ara")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = searchState) {
                is SearchState.Idle -> {
                    Button(
                        onClick = { viewModel.startSelfChat() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kaydedilen Mesajlar (Kendine Gönder)")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Veya arkadaşlarınızı e-posta adresiyle bulun.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SearchState.Loading -> {
                    CircularProgressIndicator()
                }
                is SearchState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is SearchState.Success -> {
                    UserCard(
                        user = state.user,
                        isCreatingChat = createChatState is CreateChatState.Loading,
                        onSendClick = { viewModel.startChat(state.user) }
                    )
                }
            }
            
            if (createChatState is CreateChatState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (createChatState as CreateChatState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun UserCard(user: User, isCreatingChat: Boolean, onSendClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.name, fontWeight = FontWeight.Bold)
                Text(text = user.email, style = MaterialTheme.typography.bodyMedium)
            }
            
            if (isCreatingChat) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = onSendClick) {
                    Text("Mesaj Gönder")
                }
            }
        }
    }
}
