package com.naira.assistant.presentation.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.naira.assistant.BuildConfig
import com.naira.assistant.ads.AdMobManager

@Composable
fun HomeBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val canShowAds by AdMobManager.canShowAds.collectAsStateWithLifecycle()
    val isInitialized by AdMobManager.isInitialized.collectAsStateWithLifecycle()
    val adUnitId = BuildConfig.ADMOB_HOME_BANNER_AD_UNIT_ID

    if (isPreview || !BuildConfig.ADMOB_CONFIGURED || !canShowAds || !isInitialized ||
        adUnitId.isBlank() || screenWidthDp <= 0
    ) {
        return
    }

    val adSize = remember(context, screenWidthDp) {
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
    }
    val adView = remember(context, adUnitId, adSize) { AdView(context) }

    LaunchedEffect(adView, adUnitId, adSize) {
        adView.loadAd(
            BannerAdRequest.Builder(adUnitId, adSize).build(),
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) = Unit
                override fun onAdFailedToLoad(adError: LoadAdError) = Unit
            },
        )
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { adView },
    )
}
