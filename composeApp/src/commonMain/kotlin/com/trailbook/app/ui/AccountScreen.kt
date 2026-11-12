package com.trailbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.core.sync.AccountService
import com.trailbook.core.sync.SyncOutcome
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val syncState by container.syncEngine.state.collectAsState()
    var server by remember { mutableStateOf(AccountService.DEFAULT_SERVER) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var signedIn by remember { mutableStateOf(container.accountService.restoreSession() != null) }
    var profileName by remember { mutableStateOf(container.accountService.currentSession?.profile?.displayName ?: "") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Account", style = MaterialTheme.typography.headlineSmall)
        if (!signedIn) {
            OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Sync server") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch {
                    when (val result = container.accountService.signIn(server, email, password)) {
                        is com.trailbook.core.sync.AuthResult.Success -> {
                            signedIn = true
                            profileName = result.session.profile.displayName
                            message = "Signed in as ${result.session.profile.email}"
                        }
                        is com.trailbook.core.sync.AuthResult.InvalidCredentials -> message = result.message
                        is com.trailbook.core.sync.AuthResult.NetworkError -> message = result.message
                    }
                }
            }) { Text("Sign in") }
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "The default sync server is a placeholder. Running your own sync backend at this URL enables real account sync; " +
                        "all local workflows work fully offline without an account.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text("Signed in as $profileName", style = MaterialTheme.typography.titleSmall)
            Text(
                "Last sync: " + (syncState.lastSyncAt?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString() } ?: "never"),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                scope.launch {
                    when (val outcome = container.syncEngine.sync()) {
                        is SyncOutcome.Success -> {
                            val s = outcome.state
                            message = "Sync done: ${s.uploadedTracks} tracks up, ${s.downloadedTracks} down, " +
                                "${s.uploadedMarkers} markers up, ${s.downloadedMarkers} down" +
                                if (s.conflicts.isNotEmpty()) " - ${s.conflicts.size} conflict(s) resolved keeping local changes" else ""
                        }
                        SyncOutcome.NotSignedIn -> message = "Sign in first"
                        is SyncOutcome.Offline -> message = outcome.message
                        is SyncOutcome.Failure -> message = outcome.message
                    }
                }
            }) { Text("Sync now") }
            if (syncState.conflicts.isNotEmpty()) {
                Text("Conflicts", style = MaterialTheme.typography.titleSmall)
                for (conflict in syncState.conflicts) {
                    Text(
                        "${conflict.entityType} ${conflict.entityId}: local ${conflict.localModifiedAt} vs remote ${conflict.remoteModifiedAt} (kept local)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedButton(onClick = {
                container.accountService.signOut()
                signedIn = false
                profileName = ""
                message = "Signed out"
            }) { Text("Sign out") }
        }
        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary)
    }
}
