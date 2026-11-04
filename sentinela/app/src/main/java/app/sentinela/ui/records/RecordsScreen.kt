package app.sentinela.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.sentinela.R
import app.sentinela.core.model.SessionManifest
import app.sentinela.data.room.SessionEntity
import app.sentinela.ui.SentinelaViewModel
import app.sentinela.ui.humanBytes
import app.sentinela.ui.sourceLabel
import app.sentinela.ui.sourceStateLabel

@Composable
fun RecordsScreen(vm: SentinelaViewModel, modifier: Modifier = Modifier) {
    val sessions by vm.sessions.collectAsState()
    var detail by remember { mutableStateOf<SessionManifest?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text(stringResource(R.string.records_title), style = MaterialTheme.typography.titleLarge)
        }
        if (sessions.isEmpty()) {
            item { Text(stringResource(R.string.records_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(sessions, key = { it.sessionId }) { session ->
            SessionRow(session) { detail = vm.manifestOf(session.sessionId) }
        }
    }

    detail?.let { manifest ->
        SessionDetailDialog(
            manifest = manifest,
            onDismiss = { detail = null },
            onDelete = {
                vm.delete(manifest.sessionId)
                detail = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(session: SessionEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.sessionId, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.profile, style = MaterialTheme.typography.bodySmall)
                Text(session.status, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                stringResource(R.string.records_summary, session.segmentCount, humanBytes(session.totalBytes)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SessionDetailDialog(
    manifest: SessionManifest,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) } },
        title = { Text(manifest.status.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(manifest.sessionId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Divider()
                Text(stringResource(R.string.detail_sources), style = MaterialTheme.typography.labelLarge)
                manifest.sources
                    .filter { it.value.state.name != "NOT_REQUESTED" }
                    .forEach { (kind, report) ->
                        Text(
                            "• ${sourceLabel(kind)}: ${sourceStateLabel(report.state)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                Divider()
                Text(stringResource(R.string.detail_segments), style = MaterialTheme.typography.labelLarge)
                manifest.segments.forEach { seg ->
                    Text(
                        "• ${seg.file} — ${humanBytes(seg.bytes)} — ${seg.status.name}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    seg.sha256?.let {
                        Text(
                            "   sha256: ${it.take(16)}…",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
    )
}
