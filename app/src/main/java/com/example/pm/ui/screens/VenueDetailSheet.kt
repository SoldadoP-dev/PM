package com.example.pm.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.Attendance
import com.example.pm.R
import com.example.pm.User
import com.example.pm.Venue
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.VenueDetailViewModel

@Composable
fun VenueDetailSheet(
    venue: Venue,
    navController: NavHostController,
    viewModel: VenueDetailViewModel = hiltViewModel()
) {
    val attendees by viewModel.attendees.collectAsState()
    val isAttending by viewModel.isAttending.collectAsState()
    val currentUserId = viewModel.currentUserId

    LaunchedEffect(venue.id) {
        viewModel.loadVenueDetails(venue.id)
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
        
        Text(stringResource(R.string.who_is_going), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp))
        LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            items(attendees) { attendee ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("otherProfile/${attendee.uid}") }) {
                    UserAvatar(attendee.photoUrl, attendee.username, size = 50.dp)
                    Text(if (attendee.uid == currentUserId) "Tú" else attendee.username, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (attendees.isEmpty()) {
                item { Text(stringResource(R.string.no_one_yet), color = Color.DarkGray, fontSize = 14.sp) }
            }
        }

        Button(
            onClick = { viewModel.toggleAttendance(venue.id) },
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
