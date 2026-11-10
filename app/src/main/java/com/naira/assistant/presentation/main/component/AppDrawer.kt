package com.naira.assistant.presentation.main.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naira.assistant.R
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.core.theme.CardStroke
import com.naira.assistant.core.theme.TextHigh
import com.naira.assistant.core.theme.TextSoft

/* ------------------------------- Brand ------------------------------- */

object AppBrand {
    const val ORIGIN = "Made in Bangladesh  ❤\uFE0F"
}

/* ------------------------------- Model ------------------------------- */

enum class DrawerAction(val title: String, val icon: ImageVector) {
    CUSTOMIZE("Customize", Icons.Outlined.Tune),
    PLUGINS("Plugins", Icons.Outlined.Extension),
    LIBRARY("Library", Icons.AutoMirrored.Outlined.LibraryBooks),
    SCHEDULE("Schedule", Icons.Outlined.Schedule),

    USAGE_LIMIT("Usage limit", Icons.Outlined.DataUsage),
    UPGRADE_PREMIUM("Upgrade to Premium", Icons.Outlined.WorkspacePremium),
    SETTINGS("Settings", Icons.Outlined.Settings),
    LOG_OUT("Log out", Icons.AutoMirrored.Outlined.Logout)
}

private data class DrawerSection(val label: String, val items: List<DrawerAction>)

private val DrawerSections = listOf(
    DrawerSection(
        "APP",
        listOf(
            DrawerAction.CUSTOMIZE,
            DrawerAction.PLUGINS,
            DrawerAction.LIBRARY,
            DrawerAction.SCHEDULE
        )
    ),
    DrawerSection(
        "ACCOUNT",
        listOf(
            DrawerAction.USAGE_LIMIT,
            DrawerAction.UPGRADE_PREMIUM,
            DrawerAction.SETTINGS,
            DrawerAction.LOG_OUT
        )
    )
)

/* -------------------------- Theme tokens ----------------------------- */

private val DrawerBackground = Color(0xFF080E17)
private val HeaderSurface = Color(0xFF0C1420)
private val AvatarSurface = Color(0xFF12302F)
private val Danger = Color(0xFFFF6B6B)
private val Premium = Color(0xFFFFC857)

private val DrawerWidth = 304.dp
private val RowShape = RoundedCornerShape(14.dp)
private val AvatarSize = 88.dp

/* ----------------------------- Drawer -------------------------------- */

@Composable
fun AppDrawer(
    onAction: (DrawerAction) -> Unit,
    selected: DrawerAction? = null,
    displayName: String = "User",
    greeting: String = "Good evening 🌇",
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier
            .fillMaxHeight()
            .width(DrawerWidth)
            .background(DrawerBackground)
            // Respects status bar (notch) top + gesture/nav bar bottom.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ProfileCard(name = displayName, hint = greeting)
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            DrawerSections.forEachIndexed { index, section ->
                SectionHeader(section.label)
                section.items.forEach { action ->
                    DrawerRow(
                        action = action,
                        selected = action == selected,
                        badge = if (action == DrawerAction.UPGRADE_PREMIUM) "PRO" else null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onAction(action)
                        }
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

/* ----------------------------- Profile ------------------------------- */

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
        // Border ring is now visible: the image is inset 6dp inside
        // the ring instead of being drawn flush against it.
        Box(
            Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(AvatarSurface)
                .border(1.dp, Aqua.copy(alpha = 0.55f), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = "Naira profile picture",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
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

/* ----------------------------- Sections ------------------------------ */

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        color = TextSoft.copy(alpha = 0.70f),
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CardStroke.copy(alpha = 0.60f))
    )
    Spacer(Modifier.height(8.dp))
}

/* ------------------------------- Row --------------------------------- */

@Composable
private fun DrawerRow(
    action: DrawerAction,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    val isDanger = action == DrawerAction.LOG_OUT
    val isPremium = action == DrawerAction.UPGRADE_PREMIUM

    val background by animateColorAsState(
        targetValue = when {
            selected -> Aqua.copy(alpha = 0.12f)
            isPremium -> Premium.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        label = "drawerRowBackground"
    )

    val tint = when {
        isDanger -> Danger
        isPremium -> Premium
        selected -> Aqua
        else -> TextSoft
    }
    val textColor = when {
        isDanger -> Danger
        isPremium -> TextHigh
        selected -> TextHigh
        else -> TextSoft
    }
    val fontWeight = when {
        isPremium -> FontWeight.SemiBold
        selected -> FontWeight.SemiBold
        else -> FontWeight.Medium
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RowShape)
            .background(background)
            // `selectable` gives TalkBack the correct "selected" state
            // and exposes the item as a Tab in the nav hierarchy.
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            // Guarantees ≥ 48dp touch target even with short labels.
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null, // text label is adjacent → decorative
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = action.title,
            color = textColor,
            fontSize = 14.5.sp,
            fontWeight = fontWeight,
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

/* ----------------------------- Footer -------------------------------- */

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
        Modifier.fillMaxWidth(),
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
