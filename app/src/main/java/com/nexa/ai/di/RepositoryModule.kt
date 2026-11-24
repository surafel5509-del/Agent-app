package com.nexa.ai.di

import com.nexa.ai.data.repository.AgentRepositoryImpl
import com.nexa.ai.data.repository.AuthRepositoryImpl
import com.nexa.ai.data.repository.HealthRepositoryImpl
import com.nexa.ai.data.repository.SessionRepositoryImpl
import com.nexa.ai.data.repository.SettingsRepositoryImpl
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.AuthRepository
import com.nexa.ai.domain.repository.HealthRepository
import com.nexa.ai.domain.repository.SessionRepository
import com.nexa.ai.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindAgentRepository(impl: AgentRepositoryImpl): AgentRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository
}
