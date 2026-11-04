package app.sentinela.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sentinela.R
import app.sentinela.core.model.ProfileKind
import app.sentinela.core.permissions.SentinelaPermissions
import app.sentinela.session.service.CaptureState
import app.sentinela.trigger.TriggerActivity
import app.sentinela.ui.SentinelaViewModel
import app.sentinela.ui.profileLabel

@Composable
fun HomeScreen(vm: SentinelaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by vm.captureState.collectAsState()
    val activeKind by vm.activeProfile.collectAsState()
    val profile = vm.profile(activeKind)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        context.startActivity(TriggerActivity.launchIntent(context))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.home_active_profile, profileLabel(activeKind)),
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(8.dp))
        StatusCard(state)
        Spacer(Modifier.height(8.dp))

        val busy = state is CaptureState.Active || state is CaptureState.Starting || state is CaptureState.Stopping
        Button(
            onClick = {
                val missing = SentinelaPermissions.missing(context, profile)
                if (missing.isEmpty()) {
                    context.startActivity(TriggerActivity.launchIntent(context))
                } else {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            },
            modifier = Modifier.size(180.dp),
            shape = CircleShape,
        ) {
            Text(
                text = if (busy) stringResource(R.string.home_stop) else stringResource(R.string.home_start),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))
        TransparencyCard()
    }
}

@Composable
private fun StatusCard(state: CaptureState) {
    val (text, color) = when (state) {
        CaptureState.Idle -> stringResource(R.string.status_idle) to MaterialTheme.colorScheme.surfaceVariant
        CaptureState.Starting -> stringResource(R.string.status_starting) to MaterialTheme.colorScheme.secondaryContainer
        is CaptureState.Active -> {
            val active = state.manifest.sources.count { it.value.state.name == "ACTIVE" }
            stringResource(R.string.status_active, active) to MaterialTheme.colorScheme.primaryContainer
        }
        CaptureState.Stopping -> stringResource(R.string.status_stopping) to MaterialTheme.colorScheme.secondaryContainer
        is CaptureState.Finished -> stringResource(R.string.status_finished, state.manifest.status.name) to
            MaterialTheme.colorScheme.primaryContainer
        is CaptureState.Failed -> stringResource(R.string.status_failed, state.reason) to
            MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TransparencyCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.home_transparency_title),
                style = MaterialTheme.typography.titleSmall,
                color = Color.Unspecified,
            )
            Text(
                text = stringResource(R.string.home_transparency_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
