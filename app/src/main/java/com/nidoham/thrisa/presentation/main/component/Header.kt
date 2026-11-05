package com.nidoham.thrisa.presentation.main.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.core.theme.ErrorSoft
import com.nidoham.thrisa.core.theme.Dim
import com.nidoham.thrisa.core.theme.TextHigh

// ───────────────────────── Header ─────────────────────────
@Composable
fun Header(
    onMenuClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    notificationCount: Int = 0
) {
    Box(Modifier.fillMaxWidth().heightIn(min = Dim.Touch)) {
        Box(Modifier.align(Alignment.CenterStart).size(Dim.Touch).clickable(onClick = onMenuClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Menu, "Menu", tint = TextHigh, modifier = Modifier.size(Dim.IconNav))
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text("THRISA", color = TextHigh, fontSize = 18.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.align(Alignment.CenterEnd).size(Dim.Touch).clickable(onClick = onNotificationsClick), contentAlignment = Alignment.Center) {
            Box {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = if (notificationCount > 0) "$notificationCount reminders" else "Reminders",
                    tint = TextHigh,
                    modifier = Modifier.size(Dim.IconNav)
                )
                if (notificationCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-4).dp)
                            .size(16.dp).background(ErrorSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (notificationCount > 9) "9+" else notificationCount.toString(),
                            color = TextHigh,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
