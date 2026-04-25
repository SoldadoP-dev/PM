package com.example.pm.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.Attendance
import com.example.pm.R
import com.example.pm.User
import com.example.pm.Venue
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.VenueDetailViewModel

@OptIn(ExperimentalFoundationApi::class)
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
    
    var showTagSelection by remember { mutableStateOf(false) }
    var selectedTagForList by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(venue.id) {
        viewModel.loadVenueDetails(venue.id)
    }

    if (showTagSelection) {
        TagSelectionDialog(
            availableTags = availableTags.map { it.name },
            onDismiss = { showTagSelection = false },
            onConfirm = {
                viewModel.toggleAttendance(venue.id, it)
                showTagSelection = false
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

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(venue.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = NeonPurple)
                Text("${venue.category} • ${venue.address}", color = Color.Gray, fontSize = 14.sp)
            }
            Surface(color = NeonPurple.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                    Text(venue.rating.toString(), color = NeonPurple, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        
        // ESTADÍSTICAS DE ETIQUETAS
        if (tagStats.isNotEmpty()) {
            Text("Lo que se dice de hoy (mantén pulsado):", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tagStats.toList()) { (tagName, count) ->
                    Surface(
                        modifier = Modifier.combinedClickable(
                            onClick = { /* Opcional: algún feedback? */ },
                            onLongClick = { selectedTagForList = tagName }
                        ),
                        color = NeonPurple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "$tagName $count",
                            color = NeonPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
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

        LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            items(attendees) { attendee ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("otherProfile/${attendee.uid}") }) {
                    UserAvatar(attendee.photoUrl, attendee.username, size = 50.dp)
                    Text(if (attendee.uid == viewModel.currentUserId) "Tú" else attendee.username, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (attendees.isEmpty()) {
                item { Text(stringResource(R.string.no_one_yet), color = Color.DarkGray, fontSize = 14.sp) }
            }
        }

        Button(
            onClick = { 
                if (isAttending) {
                    viewModel.toggleAttendance(venue.id) 
                } else {
                    showTagSelection = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
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
        Spacer(modifier = Modifier.height(32.dp))
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
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.Gray)
                    }
                    Button(
                        onClick = { onConfirm(selected) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        enabled = selected.isNotEmpty()
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
