package io.github.zacharycanlas.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zacharycanlas.cadence.R
import io.github.zacharycanlas.cadence.core.ACCENT_VALUES
import io.github.zacharycanlas.cadence.core.MAX_BPM
import io.github.zacharycanlas.cadence.core.MAX_RAMP_MINUTES
import io.github.zacharycanlas.cadence.core.MIN_BPM
import io.github.zacharycanlas.cadence.core.MIN_RAMP_MINUTES
import io.github.zacharycanlas.cadence.core.PRESET_BPMS
import io.github.zacharycanlas.cadence.core.Settings
import io.github.zacharycanlas.cadence.core.VOICE_IDS
import io.github.zacharycanlas.cadence.core.formatClock
import io.github.zacharycanlas.cadence.core.formatCount
import io.github.zacharycanlas.cadence.core.rampBpmAt
import io.github.zacharycanlas.cadence.core.rampDone
import io.github.zacharycanlas.cadence.run.RunState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything the screen can ask of the run. Plain lambdas, so previews and screenshots need no controller. */
class ScreenActions(
    val toggleRun: () -> Unit,
    val setCadence: (Int) -> Unit,
    val nudge: (Int) -> Unit,
    val tap: () -> Int?,
    val chooseVoice: (String) -> Unit,
    val chooseAccent: (Int) -> Unit,
    val setClick: (Boolean) -> Unit,
    val setVibrate: (Boolean) -> Unit,
    val setVolume: (Int) -> Unit,
    val setRampOn: (Boolean) -> Unit,
    val setRampTo: (Int) -> Unit,
    val setRampMinutes: (Int) -> Unit,
    val openNotificationSettings: () -> Unit,
)

/** What the screen draws beyond the run state: the beat flash and the phone's capabilities. */
data class ScreenExtras(
    /** Position of the last flashed beat in the accent cycle, while its flash is lit; null when dark. */
    val flashPosition: Int?,
    val canVibrate: Boolean,
    val notificationsDenied: Boolean,
    val tapHint: TapHint,
)

sealed interface TapHint {
    data object Idle : TapHint
    data class Counting(val taps: Int) : TapHint
    data class Reading(val bpm: Int) : TapHint
}

private val PRESET_LABELS = listOf(
    R.string.preset_recovery to R.string.preset_recovery_hint,
    R.string.preset_easy to R.string.preset_easy_hint,
    R.string.preset_steady to R.string.preset_steady_hint,
    R.string.preset_target to R.string.preset_target_hint,
    R.string.preset_tempo to R.string.preset_tempo_hint,
    R.string.preset_strides to R.string.preset_strides_hint,
)

private val VOICE_LABELS = listOf(
    R.string.voice_click to R.string.voice_click_hint,
    R.string.voice_woodblock to R.string.voice_woodblock_hint,
    R.string.voice_beep to R.string.voice_beep_hint,
    R.string.voice_cowbell to R.string.voice_cowbell_hint,
    R.string.voice_rimshot to R.string.voice_rimshot_hint,
    R.string.voice_kick to R.string.voice_kick_hint,
    R.string.voice_hihat to R.string.voice_hihat_hint,
    R.string.voice_kit to R.string.voice_kit_hint,
)

private val ACCENT_LABELS = listOf(
    R.string.accent_off to R.string.accent_off_hint,
    R.string.accent_2 to R.string.accent_2_hint,
    R.string.accent_3 to R.string.accent_3_hint,
    R.string.accent_4 to R.string.accent_4_hint,
    R.string.accent_6 to R.string.accent_6_hint,
)

private val Gutter = 20.dp

@Composable
fun CadenceScreen(
    state: RunState,
    settings: Settings,
    extras: ScreenExtras,
    actions: ScreenActions,
    snackbars: SnackbarHostState,
    announcement: String,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = { Dock(state.running, actions.toggleRun) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            Band(state, settings, extras.flashPosition, announcement)
            CadenceModule(state.currentBpm, actions, extras.tapHint)
            PresetsModule(state.currentBpm, actions.setCadence)
            SoundModule(settings, actions)
            AccentModule(settings.accentEvery, actions.chooseAccent)
            OutputModule(settings, extras, actions)
            RampModule(state, settings, actions)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The cobalt band: the one figure that owns the first viewport. */
@Composable
private fun Band(state: RunState, settings: Settings, flashPosition: Int?, announcement: String) {
    val band = LocalBandColors.current
    val cadence = pluralStringResource(R.plurals.cadence_description, state.currentBpm, state.currentBpm)
    Column(
        Modifier
            .fillMaxWidth()
            .background(band.band)
            .statusBarsPadding()
            .padding(horizontal = Gutter)
            .padding(top = 20.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(272.dp)) {
            BeatRing(settings.accentEvery, flashPosition, Modifier.fillMaxSize())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics { contentDescription = cadence },
            ) {
                Text(state.currentBpm.toString(), style = MaterialTheme.typography.displayLarge, color = band.onBand)
                Text(
                    stringResource(R.string.unit_steps_per_minute),
                    style = MaterialTheme.typography.labelSmall,
                    color = band.onBandMuted,
                )
            }
        }
        val session = if (state.running || state.elapsedSeconds > 0) {
            "${formatClock(state.elapsedSeconds)}  ·  ${pluralStringResource(R.plurals.steps, state.steps, formatCount(state.steps))}"
        } else {
            null
        }
        if (session != null) {
            Text(session, style = MaterialTheme.typography.headlineSmall, color = band.onBand, modifier = Modifier.padding(top = 4.dp))
        }
        if (climbing(state, settings)) BandSplits(state, settings)
        val status = bandStatus(state, settings)
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = band.onBandMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Spoken, not shown: Start and Stop are announced politely.
        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement })
    }
}

@Composable
private fun bandStatus(state: RunState, settings: Settings): String? = when {
    !state.running -> null
    state.silenced -> stringResource(R.string.status_paused_for_call)
    else -> rampStatus(state, settings)
}

@Composable
private fun rampStatus(state: RunState, settings: Settings): String? = when {
    !settings.rampOn -> null
    state.rampHeld -> stringResource(R.string.status_ramp_held, state.currentBpm)
    state.running && rampDone(settings.rampMinutes, state.elapsedSeconds) -> stringResource(R.string.status_ramp_holding, settings.rampTo)
    else -> stringResource(R.string.ramp_climbing_over, settings.rampTo, settings.rampMinutes)
}

private fun climbing(state: RunState, settings: Settings) =
    state.running && settings.rampOn && !state.silenced && !state.rampHeld &&
        !rampDone(settings.rampMinutes, state.elapsedSeconds)

/** The ramp's split points: minute, cadence at that minute, and which one the run has reached (-1 for none). */
private class Splits(val minutes: List<Double>, val cadences: List<Int>, val reached: Int)

private fun splits(state: RunState, settings: Settings): Splits {
    val from = if (state.running) state.rampFrom else settings.bpm
    val minutes = (0 until 6).map { it * settings.rampMinutes / 5.0 }
    val reached = if (state.running && !state.rampHeld) minutes.indexOfLast { it * 60 <= state.elapsedSeconds } else -1
    return Splits(minutes, minutes.map { rampBpmAt(from, settings.rampTo, settings.rampMinutes, it * 60) }, reached)
}

/** Where the climb has reached, on the surface a mid-run glance lands on: the split rows as one strip. */
@Composable
private fun BandSplits(state: RunState, settings: Settings) {
    val band = LocalBandColors.current
    val s = splits(state, settings)
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .border(1.dp, band.onBandMuted.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
            .clearAndSetSemantics { },
    ) {
        s.cadences.forEachIndexed { index, cadence ->
            if (index > 0) Box(Modifier.width(1.dp).height(36.dp).background(band.onBandMuted.copy(alpha = 0.5f)))
            val lit = index == s.reached
            Box(
                Modifier.weight(1f).height(36.dp).background(if (lit) band.marker else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(cadence.toString(), style = FigureBody, color = if (lit) band.onMarker else band.onBand)
            }
        }
    }
}

/**
 * Accent markers round the figure. The beat lights its marker, or the ring
 * with no accent, in the marker colour; a lit state with no movement, so it
 * reads the same with animations off.
 */
@Composable
private fun BeatRing(accentEvery: Int, flashPosition: Int?, modifier: Modifier) {
    val band = LocalBandColors.current
    val ringWidth = with(LocalDensity.current) { 3.dp.toPx() }
    val dot = with(LocalDensity.current) { 7.dp.toPx() }
    Canvas(modifier.clearAndSetSemantics { }) {
        val radius = size.minDimension / 2 - dot * 1.5f
        val lit = flashPosition != null && accentEvery == 0
        drawCircle(
            color = if (lit) band.marker else band.onBandMuted.copy(alpha = 0.45f),
            radius = radius,
            style = Stroke(if (lit) ringWidth * 2 else ringWidth),
        )
        for (position in 0 until accentEvery) {
            val angle = 2 * PI * position / accentEvery - PI / 2
            val center = Offset(this.center.x + radius * cos(angle).toFloat(), this.center.y + radius * sin(angle).toFloat())
            val on = position == flashPosition
            drawCircle(
                color = if (on) band.marker else if (position == 0) band.onBand else band.onBandMuted,
                radius = if (position == 0 || on) dot * 1.25f else dot,
                center = center,
            )
        }
    }
}

/** A ruled module under its header tab: an ink tab seated on the module's top rule, as on a printed band. */
@Composable
private fun Module(title: Int, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            stringResource(title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.surface,
            modifier = Modifier
                .padding(horizontal = Gutter)
                .background(colors.onSurface, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .semantics { heading() },
        )
        HorizontalDivider(Modifier.padding(horizontal = Gutter), thickness = 2.dp, color = colors.onSurface)
        Column(Modifier.padding(horizontal = Gutter).padding(top = 8.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 8.dp))
}

@Composable
private fun CadenceModule(bpm: Int, actions: ScreenActions, tapHint: TapHint) {
    Module(R.string.section_cadence) {
        val description = pluralStringResource(R.plurals.cadence_description, bpm, bpm)
        val sliderLabel = stringResource(R.string.cadence_slider)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(R.drawable.ic_slower, R.string.cadence_slower) { actions.nudge(-1) }
            Slider(
                value = bpm.toFloat(),
                onValueChange = { actions.setCadence(it.roundToInt()) },
                valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
                steps = MAX_BPM - MIN_BPM - 1,
                colors = untickedSlider(),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics {
                    contentDescription = sliderLabel
                    stateDescription = description
                },
            )
            StepButton(R.drawable.ic_faster, R.string.cadence_faster) { actions.nudge(1) }
        }
        OutlinedButton(
            onClick = { actions.tap() },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 64.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.tap_label), style = MaterialTheme.typography.titleMedium)
                Text(
                    when (tapHint) {
                        TapHint.Idle -> stringResource(R.string.tap_hint)
                        is TapHint.Counting -> stringResource(R.string.tap_keep_going, tapHint.taps)
                        is TapHint.Reading -> stringResource(R.string.tap_reading, tapHint.bpm)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Sliders move in whole steps; a tick drawn for each of a hundred steps reads as a dashed track. */
@Composable
private fun untickedSlider() = SliderDefaults.colors(
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledActiveTickColor = Color.Transparent,
    disabledInactiveTickColor = Color.Transparent,
)

@Composable
private fun StepButton(icon: Int, label: Int, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

/**
 * The band's grid of cells: one row of figures per option, ruled like a
 * printed table, with the chosen cell inked solid. Columns drop as the font
 * scale rises, so no label is ever clipped.
 */
@Composable
private fun <T> CellGrid(
    items: List<T>,
    columns: Int,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    sub: @Composable (T) -> String?,
) {
    val band = LocalBandColors.current
    val scale = LocalDensity.current.fontScale
    val cols = when {
        scale >= 1.7f -> minOf(columns, 2)
        scale >= 1.25f -> minOf(columns, 3)
        else -> columns
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .border(1.dp, band.rule, MaterialTheme.shapes.extraSmall)
            .selectableGroup(),
    ) {
        items.chunked(cols).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) HorizontalDivider(thickness = 1.dp, color = band.rule)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (index in 0 until cols) {
                    if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(band.rule))
                    val item = row.getOrNull(index)
                    Box(Modifier.weight(1f)) {
                        if (item != null) Cell(selected(item), { onSelect(item) }, label(item), sub(item))
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(selected: Boolean, onClick: () -> Unit, label: String, sub: String?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.onSurface else Color.Transparent,
        contentColor = if (selected) colors.surface else colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (sub != null) Text(sub, style = FigureBody, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PresetsModule(bpm: Int, setCadence: (Int) -> Unit) {
    Module(R.string.section_presets) {
        CellGrid(
            items = PRESET_BPMS.indices.toList(),
            columns = 3,
            selected = { PRESET_BPMS[it] == bpm },
            onSelect = { setCadence(PRESET_BPMS[it]) },
            label = { stringResource(PRESET_LABELS[it].first) },
            sub = { PRESET_BPMS[it].toString() },
        )
        val preset = PRESET_BPMS.indexOf(bpm)
        Hint(
            if (preset >= 0) {
                stringResource(R.string.preset_hint, stringResource(PRESET_LABELS[preset].first), stringResource(PRESET_LABELS[preset].second))
            } else {
                stringResource(R.string.preset_none_hint)
            },
        )
    }
}

@Composable
private fun SoundModule(settings: Settings, actions: ScreenActions) {
    Module(R.string.section_sound) {
        CellGrid(
            items = VOICE_IDS.indices.toList(),
            columns = 4,
            selected = { VOICE_IDS[it] == settings.voice },
            onSelect = { actions.chooseVoice(VOICE_IDS[it]) },
            label = { stringResource(VOICE_LABELS[it].first) },
            sub = { null },
        )
        Hint(stringResource(VOICE_LABELS[VOICE_IDS.indexOf(settings.voice).coerceAtLeast(0)].second))
        ValueSlider(
            label = R.string.volume,
            sliderLabel = stringResource(R.string.volume_slider),
            value = settings.volume,
            range = 0..100,
            shown = settings.volume.toString(),
            spoken = stringResource(R.string.percent, settings.volume),
            enabled = settings.click,
            onChange = actions.setVolume,
        )
    }
}

@Composable
private fun AccentModule(accentEvery: Int, chooseAccent: (Int) -> Unit) {
    Module(R.string.section_accent) {
        CellGrid(
            items = ACCENT_VALUES.indices.toList(),
            columns = 5,
            selected = { ACCENT_VALUES[it] == accentEvery },
            onSelect = { chooseAccent(ACCENT_VALUES[it]) },
            label = { stringResource(ACCENT_LABELS[it].first) },
            sub = { null },
        )
        Hint(stringResource(ACCENT_LABELS[ACCENT_VALUES.indexOf(accentEvery).coerceAtLeast(0)].second))
    }
}

@Composable
private fun OutputModule(settings: Settings, extras: ScreenExtras, actions: ScreenActions) {
    Module(R.string.section_output) {
        val vibrateOn = settings.vibrate && extras.canVibrate
        SwitchRow(
            label = stringResource(R.string.output_click),
            hint = stringResource(R.string.output_click_hint),
            checked = settings.click,
            enabled = !(settings.click && !vibrateOn),
            onChange = actions.setClick,
        )
        SwitchRow(
            label = stringResource(R.string.output_vibrate),
            hint = stringResource(if (extras.canVibrate) R.string.output_vibrate_hint else R.string.output_no_vibrator),
            checked = vibrateOn,
            enabled = extras.canVibrate && !(vibrateOn && !settings.click),
            onChange = actions.setVibrate,
        )
        Hint(stringResource(R.string.output_hint))
        if (extras.notificationsDenied) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.notifications_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.openNotificationSettings) { Text(stringResource(R.string.notifications_settings)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ValueSlider(
    label: Int,
    sliderLabel: String,
    value: Int,
    range: IntRange,
    shown: String,
    spoken: String,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val slider = @Composable { modifier: Modifier ->
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = untickedSlider(),
            enabled = enabled,
            modifier = modifier.semantics {
                contentDescription = sliderLabel
                stateDescription = spoken
            },
        )
    }
    val faded = Modifier.fillMaxWidth().padding(top = 12.dp).alpha(if (enabled) 1f else 0.38f)
    // Large text stacks the label and value over a full-width slider rather than squeezing a word.
    if (LocalDensity.current.fontScale >= 1.6f) {
        Column(faded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(shown, style = FigureBody)
            }
            slider(Modifier.fillMaxWidth())
        }
    } else {
        Row(faded, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 72.dp))
            slider(Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(shown, style = FigureBody, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 56.dp))
        }
    }
}

@Composable
private fun RampModule(state: RunState, settings: Settings, actions: ScreenActions) {
    Module(R.string.section_ramp) {
        Text(stringResource(R.string.ramp_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow(
            label = stringResource(R.string.ramp_switch),
            hint = stringResource(R.string.ramp_switch_hint),
            checked = settings.rampOn,
            enabled = true,
            onChange = actions.setRampOn,
        )
        ValueSlider(
            label = R.string.ramp_target,
            sliderLabel = stringResource(R.string.ramp_target_slider),
            value = settings.rampTo,
            range = MIN_BPM..MAX_BPM,
            shown = settings.rampTo.toString(),
            spoken = pluralStringResource(R.plurals.cadence_description, settings.rampTo, settings.rampTo),
            enabled = settings.rampOn,
            onChange = actions.setRampTo,
        )
        ValueSlider(
            label = R.string.ramp_over,
            sliderLabel = stringResource(R.string.ramp_over_slider),
            value = settings.rampMinutes,
            range = MIN_RAMP_MINUTES..MAX_RAMP_MINUTES,
            shown = stringResource(R.string.ramp_minutes, settings.rampMinutes),
            spoken = pluralStringResource(R.plurals.minutes_description, settings.rampMinutes, settings.rampMinutes),
            enabled = settings.rampOn,
            onChange = actions.setRampMinutes,
        )
        if (settings.rampOn) {
            SplitRows(state, settings)
            rampStatus(state, settings)?.let { Hint(it) }
        }
    }
}

/**
 * The signature: the ramp printed as a pace band's split rows, one shared
 * baseline and scale, the row the run has reached lit as "you are here".
 */
@Composable
private fun SplitRows(state: RunState, settings: Settings) {
    val band = LocalBandColors.current
    val s = splits(state, settings)
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.extraSmall).border(1.dp, band.rule, MaterialTheme.shapes.extraSmall),
    ) {
        s.minutes.forEachIndexed { index, minute ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = band.rule)
            val lit = index == s.reached
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (lit) band.marker else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (lit) band.onMarker else MaterialTheme.colorScheme.onSurface
                Text(stringResource(R.string.ramp_split_minute, minute.roundToInt()), style = FigureBody, color = ink, modifier = Modifier.weight(1f))
                Text(
                    s.cadences[index].toString(),
                    style = FigureBody.copy(fontWeight = FontWeight.SemiBold),
                    color = ink,
                )
            }
        }
    }
}

@Composable
private fun Dock(running: Boolean, toggleRun: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp)) {
            Button(
                onClick = toggleRun,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = MaterialTheme.shapes.large,
                colors = if (running) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)
                } else {
                    ButtonDefaults.buttonColors()
                },
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Text(stringResource(if (running) R.string.stop else R.string.start), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
