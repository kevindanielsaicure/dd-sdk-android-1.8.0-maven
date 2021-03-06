/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
internal class CrashReportsFeatureTest :
    SdkFeatureTest<Log, Configuration.Feature.CrashReport, CrashReportsFeature>() {

    var jvmExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun `set up crash reports`() {
        jvmExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @AfterEach
    fun `tear down crash reports`() {
        Thread.setDefaultUncaughtExceptionHandler(jvmExceptionHandler)
    }

    override fun createTestedFeature(): CrashReportsFeature {
        return CrashReportsFeature
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.CrashReport {
        return forge.getForgery()
    }

    @Test
    fun `???? initialize persistence strategy ???? initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(CrashLogFileStrategy::class.java)
    }

    @Test
    fun `???? create a crash reports uploader ???? createUploader()`() {
        // Given
        testedFeature.endpointUrl = fakeConfigurationFeature.endpointUrl

        // When
        val uploader = testedFeature.createUploader()

        // Then
        assertThat(uploader).isInstanceOf(LogsOkHttpUploader::class.java)
        val logsUploader = uploader as LogsOkHttpUploader
        assertThat(logsUploader.url).startsWith(fakeConfigurationFeature.endpointUrl)
        assertThat(logsUploader.client).isSameAs(CoreFeature.okHttpClient)
    }

    @Test
    fun `???? register crash handler ???? initialize`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(handler)
            .isInstanceOf(DatadogExceptionHandler::class.java)
    }

    @Test
    fun `???? restore original crash handler ???? stop()`() {
        // Given
        val mockOriginalHandler: Thread.UncaughtExceptionHandler = mock()
        Thread.setDefaultUncaughtExceptionHandler(mockOriginalHandler)

        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)
        testedFeature.stop()

        // Then
        val finalHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(finalHandler).isSameAs(mockOriginalHandler)
    }
}
