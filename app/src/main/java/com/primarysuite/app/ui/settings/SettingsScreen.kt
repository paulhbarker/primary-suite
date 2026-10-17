package com.primarysuite.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.primarysuite.app.R
import com.primarysuite.app.ui.components.SectionHeader
import com.primarysuite.app.ui.theme.AppColors

object SettingsTags {
    const val SSID = "settings.ssid"
    const val PASSWORD = "settings.password"
    const val HOST = "settings.host"
    const val PORT = "settings.port"
    const val SAVE = "settings.save"
    const val RESET = "settings.reset"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_wifi))
            Field(
                value = state.wifiSsid,
                onValueChange = onSsidChange,
                label = stringResource(R.string.settings_ssid),
                hint = stringResource(R.string.settings_default_hint, state.defaults.wifiSsid),
                tag = SettingsTags.SSID,
            )
            PasswordField(state.wifiPassword, onPasswordChange)

            SectionHeader(stringResource(R.string.settings_section_server), Modifier.padding(top = 8.dp))
            Field(
                value = state.serverHost,
                onValueChange = onHostChange,
                label = stringResource(R.string.settings_host),
                hint = stringResource(R.string.settings_default_hint, state.defaults.serverHost),
                tag = SettingsTags.HOST,
                error = if (state.hostError) stringResource(R.string.settings_host_invalid) else null,
                keyboardType = KeyboardType.Uri,
            )
            Field(
                value = state.serverPort,
                onValueChange = onPortChange,
                label = stringResource(R.string.settings_port),
                hint = stringResource(R.string.settings_default_hint, state.defaults.serverPort),
                tag = SettingsTags.PORT,
                error = if (state.portError) stringResource(R.string.settings_port_invalid) else null,
                keyboardType = KeyboardType.Number,
            )

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp).testTag(SettingsTags.SAVE),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
            ) { Text(stringResource(R.string.settings_save), fontWeight = FontWeight.Bold) }
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth().testTag(SettingsTags.RESET)) {
                Text(stringResource(R.string.settings_reset), color = AppColors.OnSurfaceMedium)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    tag: String,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        supportingText = { Text(error ?: hint) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.settings_password)) },
        supportingText = { Text(stringResource(R.string.settings_default_password_hint)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().testTag(SettingsTags.PASSWORD),
    )
}
