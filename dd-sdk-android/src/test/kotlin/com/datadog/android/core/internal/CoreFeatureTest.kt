/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.net.info.NoOpNetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.privacy.NoOpConsentProvider
import com.datadog.android.core.internal.privacy.TrackingConsentProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.system.NoOpSystemInfoProvider
import com.datadog.android.core.internal.time.KronosTimeProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.log.internal.user.DatadogUserInfoProvider
import com.datadog.android.log.internal.user.NoOpMutableUserInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.assertj.containsInstanceOf
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CoreFeatureTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    @Forgery
    lateinit var fakeCredentials: Credentials

    @Forgery
    lateinit var fakeConfig: Configuration.Core

    @StringForgery
    lateinit var fakePackageName: String
    @StringForgery(regex = "\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String
    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
    }

    @Test
    fun `???? initialize time sync ???? initialize`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.kronosClock).isNotNull()
    }

    @Test
    fun `???? initialize time provider ???? initialize`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.timeProvider)
            .isInstanceOf(KronosTimeProvider::class.java)
    }

    @Test
    fun `???? initialize system info provider ???? initialize`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.systemInfoProvider)
            .isInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `???? initialize network info provider ???? initialize {LOLLIPOP}`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        argumentCaptor<BroadcastReceiver> {
            verify(mockAppContext, atLeastOnce())
                .registerReceiver(capture(), any())

            assertThat(allValues)
                .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
                .containsInstanceOf(BroadcastReceiverNetworkInfoProvider::class.java)
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `???? initialize network info provider ???? initialize {N}`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        argumentCaptor<BroadcastReceiver> {
            verify(mockAppContext, atLeastOnce())
                .registerReceiver(capture(), any())

            assertThat(allValues)
                .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
            assertThat(allValues.none { it is BroadcastReceiverNetworkInfoProvider })
            verify(mockConnectivityMgr)
                .registerDefaultNetworkCallback(isA<CallbackNetworkInfoProvider>())
        }
    }

    @Test
    fun `???? initialize user info provider ???? initialize`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.userInfoProvider)
            .isInstanceOf(DatadogUserInfoProvider::class.java)
    }

    @Test
    fun `???? initialise the consent provider ???? initialize`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.trackingConsentProvider)
            .isInstanceOf(TrackingConsentProvider::class.java)
        assertThat(CoreFeature.trackingConsentProvider.getConsent())
            .isEqualTo(fakeConsent)
    }

    @Test
    fun `???? initializes first party hosts detector ???? initialize`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.firstPartyHostDetector.knownHosts)
            .containsAll(fakeConfig.firstPartyHosts.map { it.toLowerCase(Locale.US) })
    }

    @Test
    fun `???? initializes app info ???? initialize()`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(fakePackageVersion)
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(CoreFeature.contextRef.get()).isEqualTo(mockAppContext)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `???? initializes app info ???? initialize() {null serviceName}`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials.copy(serviceName = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(fakePackageVersion)
        assertThat(CoreFeature.serviceName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(CoreFeature.contextRef.get()).isEqualTo(mockAppContext)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `???? initializes app info ???? initialize() {null rumApplicationId}`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials.copy(rumApplicationId = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(fakePackageVersion)
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isNull()
        assertThat(CoreFeature.contextRef.get()).isEqualTo(mockAppContext)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `???? initializes app info ???? initialize() {null versionName}`(
        @IntForgery(min = 0) versionCode: Int
    ) {
        // Given
        mockAppContext = mockContext(fakePackageName, null, versionCode)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials.copy(rumApplicationId = null),
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(versionCode.toString())
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isNull()
        assertThat(CoreFeature.contextRef.get()).isEqualTo(mockAppContext)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `???? initialize okhttp with strict network policy ???? initialize() {LOLLIPOP}`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = false),
            fakeConsent
        )

        // Then
        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.RESTRICTED_TLS)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.KITKAT)
    fun `???? initialize okhttp with compat network policy ???? initialize() {KITKAT}`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = false),
            fakeConsent
        )

        // Then
        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.MODERN_TLS)
    }

    @Test
    fun `???? initialize okhttp with no network policy ???? initialize() {needsClearText}`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig.copy(needsClearTextHttp = true),
            fakeConsent
        )

        // Then
        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.CLEARTEXT)
    }

    @Test
    fun `???? initialize executors ???? initialize()`() {
        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.uploadExecutorService).isNotNull()
        assertThat(CoreFeature.persistenceExecutorService).isNotNull()
    }

    @Test
    fun `???? initialize only once ???? initialize() twice`(
        @Forgery otherCredentials: Credentials
    ) {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        CoreFeature.initialize(
            mockAppContext,
            otherCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo(fakeCredentials.clientToken)
        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(fakePackageVersion)
        assertThat(CoreFeature.serviceName).isEqualTo(fakeCredentials.serviceName)
        assertThat(CoreFeature.envName).isEqualTo(fakeCredentials.envName)
        assertThat(CoreFeature.variant).isEqualTo(fakeCredentials.variant)
        assertThat(CoreFeature.rumApplicationId).isEqualTo(fakeCredentials.rumApplicationId)
        assertThat(CoreFeature.contextRef.get()).isEqualTo(mockAppContext)
        assertThat(CoreFeature.batchSize).isEqualTo(fakeConfig.batchSize)
        assertThat(CoreFeature.uploadFrequency).isEqualTo(fakeConfig.uploadFrequency)
    }

    @Test
    fun `???? detect current process ???? initialize() {main process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(mockAppContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), fakePackageName)
        val otherProcess = forgeAppProcessInfo(Process.myPid() + 1, otherProcessName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.isMainProcess).isTrue()
    }

    @Test
    fun `???? detect current process ???? initialize() {secondary process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(mockAppContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val myProcess = forgeAppProcessInfo(Process.myPid(), otherProcessName)
        val otherProcess = forgeAppProcessInfo(Process.myPid() + 1, fakePackageName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(myProcess, otherProcess))

        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.isMainProcess).isFalse()
    }

    @Test
    fun `???? detect current process ???? initialize() {unknown process}`(
        @StringForgery otherProcessName: String
    ) {
        // Given
        val mockActivityManager = mock<ActivityManager>()
        whenever(mockAppContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(
            mockActivityManager
        )
        val otherProcess = forgeAppProcessInfo(Process.myPid() + 1, otherProcessName)
        whenever(mockActivityManager.runningAppProcesses)
            .thenReturn(listOf(otherProcess))

        // When
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // Then
        assertThat(CoreFeature.isMainProcess).isTrue()
    }

    @Test
    fun `???? cleanup app info ???? stop()`() {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        CoreFeature.stop()

        // Then
        assertThat(CoreFeature.clientToken).isEqualTo("")
        assertThat(CoreFeature.packageName).isEqualTo("")
        assertThat(CoreFeature.packageVersion).isEqualTo("")
        assertThat(CoreFeature.serviceName).isEqualTo("")
        assertThat(CoreFeature.envName).isEqualTo("")
        assertThat(CoreFeature.variant).isEqualTo("")
        assertThat(CoreFeature.rumApplicationId).isNull()
        assertThat(CoreFeature.contextRef.get()).isNull()
    }

    @Test
    fun `???? cleanup providers ???? stop()`() {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        CoreFeature.stop()

        // Then
        assertThat(CoreFeature.firstPartyHostDetector.knownHosts)
            .isEmpty()
        assertThat(CoreFeature.networkInfoProvider)
            .isInstanceOf(NoOpNetworkInfoProvider::class.java)
        assertThat(CoreFeature.systemInfoProvider)
            .isInstanceOf(NoOpSystemInfoProvider::class.java)
        assertThat(CoreFeature.timeProvider)
            .isInstanceOf(NoOpTimeProvider::class.java)
        assertThat(CoreFeature.trackingConsentProvider)
            .isInstanceOf(NoOpConsentProvider::class.java)
        assertThat(CoreFeature.userInfoProvider)
            .isInstanceOf(NoOpMutableUserInfoProvider::class.java)
    }

    @Test
    fun `???? shut down executors ???? stop()`() {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )
        val mockUploadExecutorService: ScheduledThreadPoolExecutor = mock()
        CoreFeature.uploadExecutorService = mockUploadExecutorService
        val mockPersistenceExecutorService: ExecutorService = mock()
        CoreFeature.persistenceExecutorService = mockPersistenceExecutorService

        // When
        CoreFeature.stop()

        // Then
        verify(mockUploadExecutorService).shutdownNow()
        verify(mockPersistenceExecutorService).shutdownNow()
    }

    @Test
    fun `???? unregister tracking consent callbacks ???? stop()`() {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )
        val mockConsentProvider: ConsentProvider = mock()
        CoreFeature.trackingConsentProvider = mockConsentProvider

        // When
        CoreFeature.stop()

        // Then
        verify(mockConsentProvider).unregisterAllCallbacks()
    }

    @Test
    fun `???? build config ???? buildFilePersistenceConfig()`() {
        // Given
        CoreFeature.initialize(
            mockAppContext,
            fakeCredentials,
            fakeConfig,
            fakeConsent
        )

        // When
        val config = CoreFeature.buildFilePersistenceConfig()

        // Then
        assertThat(config.maxBatchSize)
            .isEqualTo(FilePersistenceConfig.MAX_BATCH_SIZE)
        assertThat(config.maxDiskSpace)
            .isEqualTo(FilePersistenceConfig.MAX_DISK_SPACE)
        assertThat(config.oldFileThreshold)
            .isEqualTo(FilePersistenceConfig.OLD_FILE_THRESHOLD)
        assertThat(config.maxItemsPerBatch)
            .isEqualTo(FilePersistenceConfig.MAX_ITEMS_PER_BATCH)
        assertThat(config.recentDelayMs)
            .isEqualTo(fakeConfig.batchSize.windowDurationMs)
    }

    // region Internal

    private fun forgeAppProcessInfo(
        processId: Int,
        processName: String
    ): ActivityManager.RunningAppProcessInfo {
        return ActivityManager.RunningAppProcessInfo(
            "",
            0,
            emptyArray()
        ).apply {
            this.processName = processName
            this.pid = processId
        }
    }

    // endregion
}
