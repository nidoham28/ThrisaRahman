package com.nidoham.thrisa.presentation.main.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.R
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.CardStroke
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.theme.TextSoft

/* ------------------------------------------------------------------ */
/*  Brand                                                              */
/* ------------------------------------------------------------------ */

object AppBrand {
    const val ORIGIN = "Made in Bangladesh  ❤\uFE0F"
}

/* ------------------------------------------------------------------ */
/*  Model                                                              */
/* ------------------------------------------------------------------ */

enum class DrawerAction(val title: String, val icon: ImageVector) {
    // APP
    CUSTOMIZE("Customize", Icons.Outlined.Tune),
    PLUGINS("Plugins", Icons.Outlined.Extension),
    LIBRARY("Library", Icons.AutoMirrored.Outlined.LibraryBooks),
    SCHEDULE("Schedule", Icons.Outlined.Schedule),

    // ACCOUNT
    USAGE_LIMIT("Usage limit", Icons.Outlined.DataUsage),
    UPGRADE_PREMIUM("Upgrade to Premium", Icons.Outlined.WorkspacePremium),
    SETTINGS("Settings", Icons.Outlined.Settings),
    LOG_OUT("Log out", Icons.AutoMirrored.Outlined.Logout)
}

private data class DrawerSection(val label: String, val items: List<DrawerAction>)

private val DrawerSections = listOf(
    DrawerSection(
        label = "APP",
        items = listOf(
            DrawerAction.CUSTOMIZE,
            DrawerAction.PLUGINS,
            DrawerAction.LIBRARY,
            DrawerAction.SCHEDULE
        )
    ),
    DrawerSection(
        label = "ACCOUNT",
        items = listOf(
            DrawerAction.USAGE_LIMIT,
            DrawerAction.UPGRADE_PREMIUM,
            DrawerAction.SETTINGS,
            DrawerAction.LOG_OUT
        )
    )
)

/* ------------------------------------------------------------------ */
/*  Theme tokens local to the drawer                                   */
/* ------------------------------------------------------------------ */

private val DrawerBackground = Color(0xFF080E17)
private val HeaderSurface = Color(0xFF0C1420)
private val AvatarSurface = Color(0xFF12302F)
private val Danger = Color(0xFFFF6B6B)
private val Premium = Color(0xFFFFC857)

/* ------------------------------------------------------------------ */
/*  Drawer                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun AppDrawer(
    onAction: (DrawerAction) -> Unit,
    selected: DrawerAction? = null,
    displayname: String = "User",
    greeting: String = "Good evening 🌇"
) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(DrawerBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        ProfileCard(name = displayname, hint = greeting)
        Spacer(Modifier.height(6.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            DrawerSections.forEachIndexed { index, section ->
                SectionHeader(section.label)
                section.items.forEach { action ->
                    DrawerRow(
                        action = action,
                        selected = action == selected,
                        badge = if (action == DrawerAction.UPGRADE_PREMIUM) "PRO" else null,
                        onClick = { onAction(action) }
                    )
                }
                if (index != DrawerSections.lastIndex) SectionDivider()
            }
            Spacer(Modifier.height(12.dp))
        }

        DrawerFooter()
        Spacer(Modifier.height(16.dp))
    }
}

/* ------------------------------------------------------------------ */
/*  Parts                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun ProfileCard(name: String, hint: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HeaderSurface)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(1.dp, Aqua.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = name,
            color = TextHigh,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = hint,
            color = TextSoft,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        color = TextSoft.copy(alpha = 0.70f),
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CardStroke.copy(alpha = 0.60f))
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun DrawerRow(
    action: DrawerAction,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    val isDanger = action == DrawerAction.LOG_OUT
    val isPremium = action == DrawerAction.UPGRADE_PREMIUM
    val tint = when {
        isDanger -> Danger
        isPremium -> Premium
        selected -> Aqua
        else -> TextSoft
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected -> Aqua.copy(alpha = 0.12f)
                    isPremium -> Premium.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = action.title,
            color = when {
                isDanger -> Danger
                isPremium -> TextHigh
                selected -> TextHigh
                else -> TextSoft
            },
            fontSize = 14.5.sp,
            fontWeight = when {
                isPremium -> FontWeight.SemiBold
                selected -> FontWeight.SemiBold
                else -> FontWeight.Medium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Badge(badge, accent = if (isPremium) Premium else Aqua)
        }
    }
}

@Composable
private fun Badge(text: String, accent: Color = Aqua) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = accent,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DrawerFooter() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CardStroke)
    )
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = AppBrand.ORIGIN,
            color = TextSoft.copy(alpha = 0.55f),
            fontSize = 12.sp,
            letterSpacing = 0.3.sp
        )
    }
}