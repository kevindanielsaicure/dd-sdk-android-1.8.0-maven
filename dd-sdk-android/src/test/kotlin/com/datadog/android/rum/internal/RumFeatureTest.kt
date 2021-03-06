/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.rum.internal.domain.RumFileStrategy
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.instrumentation.gestures.NoOpGesturesTracker
import com.datadog.android.rum.internal.net.RumOkHttpUploader
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ref.WeakReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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
internal class RumFeatureTest : SdkFeatureTest<RumEvent, Configuration.Feature.RUM, RumFeature>() {

    override fun createTestedFeature(): RumFeature {
        return RumFeature
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.RUM {
        return forge.getForgery()
    }

    @Test
    fun `???? initialize persistence strategy ???? initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(RumFileStrategy::class.java)
    }

    @Test
    fun `???? create a logs uploader ???? createUploader()`() {
        // Given
        testedFeature.endpointUrl = fakeConfigurationFeature.endpointUrl

        // When
        val uploader = testedFeature.createUploader()

        // Then
        assertThat(uploader).isInstanceOf(RumOkHttpUploader::class.java)
        val logsUploader = uploader as RumOkHttpUploader
        assertThat(logsUploader.url).startsWith(fakeConfigurationFeature.endpointUrl)
        assertThat(logsUploader.client).isSameAs(CoreFeature.okHttpClient)
    }

    @Test
    fun `???? store sampling rate ???? initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.samplingRate).isEqualTo(fakeConfigurationFeature.samplingRate)
    }

    @Test
    fun `???? store gesturesTracker ???? initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.gesturesTracker)
            .isEqualTo(fakeConfigurationFeature.gesturesTracker)
    }

    @Test
    fun `???? store and register viewTrackingStrategy ???? initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isEqualTo(fakeConfigurationFeature.viewTrackingStrategy)
        verify(fakeConfigurationFeature.viewTrackingStrategy!!).register(mockAppContext)
    }

    @Test
    fun `???? store userActionTrackingStrategy ???? initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isEqualTo(fakeConfigurationFeature.userActionTrackingStrategy)
        verify(fakeConfigurationFeature.userActionTrackingStrategy!!).register(mockAppContext)
    }

    @Test
    fun `???? use noop gesturesTracker ???? initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(gesturesTracker = null)

        // When
        testedFeature.initialize(mockAppContext, config)

        // Then
        assertThat(testedFeature.gesturesTracker)
            .isInstanceOf(NoOpGesturesTracker::class.java)
    }

    @Test
    fun `???? use noop viewTrackingStrategy ???? initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(viewTrackingStrategy = null)

        // When
        testedFeature.initialize(mockAppContext, config)

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `???? use noop userActionTrackingStrategy ???? initialize()`() {
        // Given
        val config = fakeConfigurationFeature.copy(userActionTrackingStrategy = null)

        // When
        testedFeature.initialize(mockAppContext, config)

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `???? register viewTreeStrategy ???? initialize()`() {
        // When
        val mockViewTreeStrategy: TrackingStrategy = mock()
        testedFeature.viewTreeTrackingStrategy = mockViewTreeStrategy
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        verify(mockViewTreeStrategy).register(mockAppContext)
    }

    @Test
    fun `???? store eventMapper ???? initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.rumEventMapper).isSameAs(fakeConfigurationFeature.rumEventMapper)
    }

    @Test
    fun `???? use noop gesturesTracker ???? stop()`() {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.gesturesTracker)
            .isInstanceOf(NoOpGesturesTracker::class.java)
    }

    @Test
    fun `???? use noop viewTrackingStrategy ???? stop()`() {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.viewTrackingStrategy)
            .isInstanceOf(NoOpViewTrackingStrategy::class.java)
    }

    @Test
    fun `???? use noop userActionTrackingStrategy ???? stop()`() {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
    }

    @Test
    fun `???? unregister strategies ???? stop()`() {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)
        CoreFeature.contextRef = WeakReference(mockAppContext)
        val mockActionTrackingStrategy: UserActionTrackingStrategy = mock()
        val mockViewTrackingStrategy: ViewTrackingStrategy = mock()
        val mockViewTreeTrackingStrategy: TrackingStrategy = mock()
        testedFeature.actionTrackingStrategy = mockActionTrackingStrategy
        testedFeature.viewTrackingStrategy = mockViewTrackingStrategy
        testedFeature.viewTreeTrackingStrategy = mockViewTreeTrackingStrategy

        // When
        testedFeature.stop()

        // Then
        verify(mockActionTrackingStrategy).unregister(mockAppContext)
        verify(mockViewTrackingStrategy).unregister(mockAppContext)
        verify(mockViewTreeTrackingStrategy).unregister(mockAppContext)
    }

    @Test
    fun `???? reset eventMapper ???? stop()`() {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.rumEventMapper).isInstanceOf(NoOpEventMapper::class.java)
    }
}
