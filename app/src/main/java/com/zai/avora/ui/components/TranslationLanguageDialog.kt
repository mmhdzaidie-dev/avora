package com.zai.avora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zai.avora.data.lyrics.TranslationLanguages
import com.zai.avora.ui.theme.AvoraAppTheme
import com.zai.avora.ui.theme.Typography

@Composable
fun TranslationLanguageDialog(
    enabled: Boolean,
    selectedCode: String,
    onSelectLanguage: (String) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AvoraAppTheme.colors
    var query by remember { mutableStateOf("") }
    val languages = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) TranslationLanguages.all
        else TranslationLanguages.all.filter {
            it.name.contains(q, ignoreCase = true) || it.code.contains(q, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "Terjemahan lirik",
                style = Typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (query.isEmpty()) {
                    Text("Cari bahasa…", style = Typography.bodyMedium, color = colors.textMuted)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = Typography.bodyMedium.copy(color = colors.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(colors.textPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                if (query.isBlank()) {
                    item {
                        LanguageRow(
                            label = "Tanpa terjemahan",
                            selected = !enabled,
                            onClick = { onDisable(); onDismiss() }
                        )
                    }
                }
                items(languages, key = { it.code }) { lang ->
                    LanguageRow(
                        label = lang.name,
                        trailing = TranslationLanguages.shortLabel(lang.code),
                        selected = enabled && lang.code == selectedCode,
                        onClick = { onSelectLanguage(lang.code); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null
) {
    val colors = AvoraAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = Typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(text = trailing, style = Typography.labelSmall, color = colors.textMuted)
            Spacer(Modifier.width(8.dp))
        }
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
