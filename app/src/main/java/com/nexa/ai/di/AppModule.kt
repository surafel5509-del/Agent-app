package com.nexa.ai.di

import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.clock.SystemClock
import com.nexa.ai.core.crypto.KeystoreTokenCipher
import com.nexa.ai.core.crypto.TokenCipher
import com.nexa.ai.core.dispatcher.DefaultDispatcherProvider
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.core.network.ConnectivityMonitor
import com.nexa.ai.core.network.ConnectivityObserver
import com.nexa.ai.data.local.SecureTokenStore
import com.nexa.ai.data.local.TokenStore
import com.nexa.ai.data.remote.parser.RemoteParser
import com.nexa.ai.domain.polling.PollingConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun providePollingConfig(): PollingConfig = PollingConfig.Default

    /** [RemoteParser] is a stateless `object`, so Dagger cannot construct it by itself. */
    @Provides
    @Singleton
    fun provideRemoteParser(): RemoteParser = RemoteParser
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreBindsModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: ConnectivityObserver): ConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindTokenCipher(impl: KeystoreTokenCipher): TokenCipher

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: SecureTokenStore): TokenStore
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(provider: DispatcherProvider): CoroutineDispatcher = provider.io

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(provider: DispatcherProvider): CoroutineDispatcher = provider.main

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(provider: DispatcherProvider): CoroutineDispatcher = provider.default
}
