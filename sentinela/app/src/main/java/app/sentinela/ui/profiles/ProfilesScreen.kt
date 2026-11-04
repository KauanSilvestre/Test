package app.sentinela.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sentinela.R
import app.sentinela.core.model.CameraLens
import app.sentinela.core.model.ProfileKind
import app.sentinela.core.model.RecordingProfile
import app.sentinela.core.model.VideoQuality
import app.sentinela.ui.SentinelaViewModel
import app.sentinela.ui.profileLabel

@Composable
fun ProfilesScreen(vm: SentinelaViewModel, modifier: Modifier = Modifier) {
    val activeKind by vm.activeProfile.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.profiles_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        items(ProfileKind.entries) { kind ->
            ProfileCard(
                kind = kind,
                isActive = kind == activeKind,
                initial = vm.profile(kind),
                onActivate = { vm.setActiveProfile(kind) },
                onChange = { vm.saveProfile(it) },
            )
        }
    }
}

@Composable
private fun ProfileCard(
    kind: ProfileKind,
    isActive: Boolean,
    initial: RecordingProfile,
    onActivate: () -> Unit,
    onChange: (RecordingProfile) -> Unit,
) {
    var profile by remember(kind) { mutableStateOf(initial) }

    fun update(block: (RecordingProfile) -> RecordingProfile) {
        profile = block(profile)
        onChange(profile)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isActive, onClick = onActivate),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isActive, onClick = onActivate)
                Text(profileLabel(kind), style = MaterialTheme.typography.titleMedium)
            }

            if (kind.usesCameraConfig()) {
                Divider()
                Text(stringResource(R.string.profiles_camera_lens), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CameraLens.entries.forEach { lens ->
                        FilterChip(
                            selected = profile.cameraLens == lens,
                            onClick = { update { it.copy(cameraLens = lens) } },
                            label = { Text(lensLabel(lens)) },
                        )
                    }
                }
                Text(stringResource(R.string.profiles_quality), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoQuality.entries.forEach { q ->
                        FilterChip(
                            selected = profile.videoQuality == q,
                            onClick = { update { it.copy(videoQuality = q) } },
                            label = { Text(q.name) },
                        )
                    }
                }
            }

            Divider()
            Text(stringResource(R.string.profiles_segment), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingProfile.SEGMENT_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = profile.segmentMinutes == minutes,
                        onClick = { update { it.copy(segmentMinutes = minutes) } },
                        label = { Text(stringResource(R.string.profiles_minutes, minutes)) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.profiles_vibrate))
                Switch(checked = profile.vibrate, onCheckedChange = { v -> update { it.copy(vibrate = v) } })
            }

            if (kind == ProfileKind.SCREEN_AUDIO || kind == ProfileKind.COMPLETE) {
                Text(
                    text = stringResource(R.string.profiles_screen_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun ProfileKind.usesCameraConfig() = this == ProfileKind.CAMERA_AUDIO || this == ProfileKind.COMPLETE

@Composable
private fun lensLabel(lens: CameraLens): String = stringResource(
    when (lens) {
        CameraLens.REAR -> R.string.lens_rear
        CameraLens.FRONT -> R.string.lens_front
        CameraLens.BOTH -> R.string.lens_both
    },
)
