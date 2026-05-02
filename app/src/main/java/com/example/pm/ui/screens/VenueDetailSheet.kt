package com.example.pm.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.ChatRoom
import com.example.pm.R
import com.example.pm.User
import com.example.pm.Venue
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.VenueDetailViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VenueDetailSheet(
    venue: Venue,
    navController: NavHostController,
    viewModel: VenueDetailViewModel = hiltViewModel()
) {
    val attendees by viewModel.attendees.collectAsState()
    val isAttending by viewModel.isAttending.collectAsState()
    val attendancesCount by viewModel.attendancesCount.collectAsState()
    val tagStats by viewModel.tagStats.collectAsState()
    val tagUsers by viewModel.tagUsers.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val otherVenueAttendance by viewModel.otherVenueAttendance.collectAsState()
    val hasOtherAttendance by viewModel.hasOtherAttendance.collectAsState()
    
    val followers by viewModel.followers.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val venueAttendeeUids by viewModel.venueAttendeeUids.collectAsState()

    var showTagSelection by remember { mutableStateOf(false) }
    var selectedTagForList by remember { mutableStateOf<String?>(null) }
    var showAttendanceConflictDialog by remember { mutableStateOf(false) }
    var showInviteConfirmDialog by remember { mutableStateOf(false) }
    var showInviteSelectionDialog by remember { mutableStateOf(false) }

    // Participant search and filter
    var participantSearchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }

    val filteredAttendees = remember(attendees, participantSearchQuery, selectedTagFilter, tagUsers) {
        attendees.filter { user ->
            val nameMatches = user.username.contains(participantSearchQuery, ignoreCase = true)
            val tagMatches = if (selectedTagFilter == null) true
                            else tagUsers[selectedTagFilter]?.any { it.uid == user.uid } == true
            nameMatches && tagMatches
        }
    }

    LaunchedEffect(venue.id) {
        viewModel.loadVenueDetails(venue.id)
    }

    // Diálogo de Confirmación de cambio de planes
    if (showAttendanceConflictDialog) {
        AlertDialog(
            onDismissRequest = { showAttendanceConflictDialog = false },
            title = { Text(text = "¿Cambiar de planes?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                val otherName = otherVenueAttendance?.name ?: "otra discoteca"
                Text(
                    text = "Ya estás apuntado en $otherName. Si te unes a ${venue.name}, se cancelará tu reserva anterior.",
                    color = Color.LightGray,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCurrentAttendance()
                        showAttendanceConflictDialog = false
                        showTagSelection = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Sí, cambiar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAttendanceConflictDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = CardGray,
            shape = RoundedCornerShape(28.dp)
        )
    }

    // Diálogo de Confirmación para invitar
    if (showInviteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showInviteConfirmDialog = false },
            title = { Text(text = "¿Invitar amigos?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "¿Quieres mandar una solicitud de unión a tus seguidores y chats para que se unan a ti en ${venue.name}?",
                    color = Color.LightGray,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInviteConfirmDialog = false
                        showInviteSelectionDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Si", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteConfirmDialog = false }) {
                    Text("Ahora no", color = Color.Gray)
                }
            },
            containerColor = CardGray,
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showInviteSelectionDialog) {
        InviteSelectionDialog(
            followers = followers,
            chats = chats,
            venueAttendeeUids = venueAttendeeUids,
            onDismiss = { showInviteSelectionDialog = false },
            onSend = { selectedUsers, selectedChats ->
                viewModel.sendInvitations(venue.id, venue.name, selectedUsers, selectedChats)
                showInviteSelectionDialog = false
            }
        )
    }

    if (showTagSelection) {
        TagSelectionDialog(
            availableTags = availableTags.map { it.name },
            onDismiss = { showTagSelection = false },
            onConfirm = { tags ->
                viewModel.toggleAttendance(venue.id, tags)
                showTagSelection = false
                showInviteConfirmDialog = true
            }
        )
    }

    if (selectedTagForList != null) {
        val users = tagUsers[selectedTagForList] ?: emptyList()
        TagUsersDialog(
            tagName = selectedTagForList!!,
            users = users,
            onDismiss = { selectedTagForList = null },
            onUserClick = { userId ->
                selectedTagForList = null
                navController.navigate("otherProfile/$userId")
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (venue.photoUrl.isNotEmpty()) {
            coil.compose.AsyncImage(
                model = venue.photoUrl,
                contentDescription = "Foto de ${venue.name}",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(venue.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = NeonPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(color = NeonPurple.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                            Text(venue.rating.toString(), color = NeonPurple, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                Text("${venue.category} • ${venue.address}", color = Color.Gray, fontSize = 14.sp)
            }
        }
        
        Row(modifier = Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.who_is_going), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Surface(color = Color.DarkGray, shape = CircleShape) {
                Text(
                    text = attendancesCount.toString(),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(40.dp)
                .background(Color(0xFF262626), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = participantSearchQuery,
                    onValueChange = { participantSearchQuery = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (participantSearchQuery.isEmpty()) {
                            Text("Buscar por nombre...", color = Color.Gray, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        if (tagStats.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedTagFilter == null,
                        onClick = { selectedTagFilter = null },
                        label = { Text("Todos", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple,
                            containerColor = Color.DarkGray,
                            labelColor = Color.Gray,
                            selectedLabelColor = Color.Black
                        ),
                        border = null
                    )
                }
                tagStats.keys.forEach { tagName ->
                    item {
                        FilterChip(
                            selected = selectedTagFilter == tagName,
                            onClick = { selectedTagFilter = if (selectedTagFilter == tagName) null else tagName },
                            label = { Text(tagName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                containerColor = Color.DarkGray,
                                labelColor = Color.Gray,
                                selectedLabelColor = Color.Black
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            items(filteredAttendees) { attendee ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("otherProfile/${attendee.uid}") }) {
                    UserAvatar(attendee.photoUrl, attendee.username, size = 50.dp)
                    Text(if (attendee.uid == viewModel.currentUserId) "Tú" else attendee.username, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (filteredAttendees.isEmpty()) {
                item { 
                    Text(
                        text = if (participantSearchQuery.isEmpty()) stringResource(R.string.no_one_yet) else "No se han encontrado resultados", 
                        color = Color.DarkGray, 
                        fontSize = 14.sp 
                    ) 
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { 
                    if (isAttending) {
                        viewModel.toggleAttendance(venue.id) 
                    } else {
                        if (hasOtherAttendance) {
                            showAttendanceConflictDialog = true
                        } else {
                            showTagSelection = true
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isAttending) Color.DarkGray else NeonPurple)
            ) {
                Icon(if (isAttending) Icons.Default.CheckCircle else Icons.Default.Add, null, tint = if (isAttending) NeonPurple else Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isAttending) stringResource(R.string.im_going) else stringResource(R.string.join_me), 
                    color = if (isAttending) Color.White else Color.Black, 
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            if (isAttending) {
                IconButton(
                    onClick = { showInviteSelectionDialog = true },
                    modifier = Modifier.size(56.dp).background(NeonPurple, RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectionDialog(
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardGray)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Elige hasta 3 etiquetas", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("¿Qué plan hay hoy?", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    availableTags.forEach { tag ->
                        val isSelected = selected.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selected = selected - tag
                                } else if (selected.size < 3) {
                                    selected = selected + tag
                                }
                            },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                labelColor = Color.White,
                                selectedLabelColor = Color.Black,
                                containerColor = Color.DarkGray
                            )
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selected) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Confirmar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TagUsersDialog(
    tagName: String,
    users: List<User>,
    onDismiss: () -> Unit,
    onUserClick: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardGray)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tagName,
                        color = NeonPurple,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                    }
                }
                Text("Usuarios que eligieron esta etiqueta:", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
                
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(users) { user ->
                        ListItem(
                            modifier = Modifier.clickable { onUserClick(user.uid) },
                            headlineContent = { Text(user.username, color = Color.White, fontWeight = FontWeight.Bold) },
                            leadingContent = { UserAvatar(user.photoUrl, user.username, 40.dp) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    if (users.isEmpty()) {
                        item {
                            Text("Cargando usuarios...", color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InviteSelectionDialog(
    followers: List<User>,
    chats: List<ChatRoom>,
    venueAttendeeUids: Set<String>,
    onDismiss: () -> Unit,
    onSend: (List<String>, List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedUserIds = remember { mutableStateListOf<String>() }
    val selectedChatIds = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Invitar a la quedada", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                
                // Search Bar
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(0xFF262626), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(NeonPurple),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) Text("Buscar...", color = Color.Gray, fontSize = 15.sp)
                                innerTextField()
                            }
                        )
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    // SECCIÓN CHATS
                    val filteredChats = chats.filter { 
                        it.isGroup && (it.name?.contains(searchQuery, ignoreCase = true) == true)
                    }
                    if (filteredChats.isNotEmpty()) {
                        item {
                            Text("GRUPOS", color = NeonPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(filteredChats) { chat ->
                            val isSelected = selectedChatIds.contains(chat.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (isSelected) selectedChatIds.remove(chat.id) else selectedChatIds.add(chat.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(chat.photoUrl, chat.name ?: "?", 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(chat.name ?: "Grupo", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = NeonPurple, uncheckedColor = Color.Gray)
                                )
                            }
                        }
                    }

                    // SECCIÓN SEGUIDORES
                    val filteredFollowers = followers.filter { 
                        it.username.contains(searchQuery, ignoreCase = true) 
                    }
                    if (filteredFollowers.isNotEmpty()) {
                        item {
                            Text("SEGUIDORES", color = NeonPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                        }
                        items(filteredFollowers) { user ->
                            val isAttending = venueAttendeeUids.contains(user.uid)
                            val isSelected = selectedUserIds.contains(user.uid)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAttending) { 
                                        if (isSelected) selectedUserIds.remove(user.uid) else selectedUserIds.add(user.uid) 
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(user.photoUrl, user.username, 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.username, color = if (isAttending) Color.Gray else Color.White, fontWeight = FontWeight.Medium)
                                    if (isAttending) {
                                        Text("Ya está apuntado", color = NeonPurple, fontSize = 11.sp)
                                    }
                                }
                                if (!isAttending) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = NeonPurple, uncheckedColor = Color.Gray)
                                    )
                                } else {
                                    Icon(Icons.Default.Check, null, tint = NeonPurple, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.Gray)
                    }
                    Button(
                        onClick = { onSend(selectedUserIds.toList(), selectedChatIds.toList()) },
                        enabled = selectedUserIds.isNotEmpty() || selectedChatIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Enviar invitación", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeholders.forEach { placeable ->
            if (currentRowWidth + placeable.width + mainAxisSpacing.roundToPx() > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        rows.add(currentRow)

        val height = rows.sumOf { row -> row.maxOfOrNull { it.height } ?: 0 } + (rows.size - 1) * crossAxisSpacing.roundToPx()
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                }
                y += rowHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}
