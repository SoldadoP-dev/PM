package com.example.pm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.R
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.viewmodels.UserListViewModel

@Composable
fun UserListScreen(
    navController: NavHostController,
    type: String,
    userId: String,
    viewModel: UserListViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val users by viewModel.users.collectAsState()

    LaunchedEffect(userId) {
        viewModel.loadUsers(type, userId)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.Black).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) 
                    }
                    Text(
                        if (type == "followers") stringResource(R.string.followers) else stringResource(R.string.following), 
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }
                TextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    placeholder = { Text("Buscar...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardGray, 
                        unfocusedContainerColor = CardGray, 
                        focusedIndicatorColor = Color.Transparent, 
                        unfocusedIndicatorColor = Color.Transparent, 
                        focusedTextColor = Color.White, 
                        unfocusedTextColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            items(users.filter { it.username.contains(searchQuery, true) }) { user ->
                ListItem(
                    modifier = Modifier.clickable { navController.navigate("otherProfile/${user.uid}") },
                    headlineContent = { Text(user.username, fontWeight = FontWeight.Bold, color = Color.White) },
                    leadingContent = { UserAvatar(user.photoUrl, user.username, size = 44.dp) },
                    colors = ListItemDefaults.colors(containerColor = DeepSpace)
                )
            }
        }
    }
}
