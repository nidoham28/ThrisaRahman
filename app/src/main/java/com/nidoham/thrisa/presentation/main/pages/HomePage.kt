package presentation.main.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nidoham.thrisa.core.sound.VoiceState
import com.nidoham.thrisa.core.theme.Aqua
import com.nidoham.thrisa.core.theme.Dim
import com.nidoham.thrisa.core.theme.Lilac
import com.nidoham.thrisa.core.theme.TextHigh
import com.nidoham.thrisa.core.util.currentDateInfo
import com.nidoham.thrisa.domain.inspirations
import com.nidoham.thrisa.presentation.main.component.HeroCard
import com.nidoham.thrisa.presentation.main.component.InfoCard
import com.nidoham.thrisa.presentation.main.component.InspirationCard

@Composable
fun HomePage(voice: VoiceState, displayName: String = "User") {
    val todayInfo = currentDateInfo()
    val date = todayInfo.dayNumber.toString()
    val info = "${todayInfo.dayName}, ${todayInfo.month}"

    Column(
        Modifier.fillMaxSize()
            .padding(horizontal = Dim.ScreenPad)
            .padding(bottom = Dim.Gap),
        verticalArrangement = Arrangement.spacedBy(Dim.Gap)
    ) {
        // Hero takes all remaining vertical space after the other sections are laid out.
        HeroCard(voice, Modifier.fillMaxWidth().weight(1f), displayName = displayName)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            inspirations.forEach { InspirationCard(it, Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard("Weather", "Partly cloudy", Modifier.weight(1f)) {
                Icon(Icons.Outlined.WbCloudy, null, tint = Aqua, modifier = Modifier.size(Dim.IconCard))
                Spacer(Modifier.width(6.dp))
                Text("18°", color = TextHigh, fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
            InfoCard("Today", info, Modifier.weight(1f)) {
                Text(date, color = TextHigh, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1)
            }
            InfoCard(
                title = "Mood", caption = "Everything good", modifier = Modifier.weight(1f),
                titleTrailing = { Icon(Icons.Outlined.EmojiEmotions, null, tint = Lilac, modifier = Modifier.size(14.dp)) }
            ) {
                Text("Good", color = Lilac, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}
