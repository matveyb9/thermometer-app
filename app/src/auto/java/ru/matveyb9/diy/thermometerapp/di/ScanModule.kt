package ru.matveyb9.diy.thermometerapp.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.matveyb9.diy.thermometerapp.scan.AutoScanController
import ru.matveyb9.diy.thermometerapp.scan.ScanController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds
    @Singleton
    abstract fun bindScanController(impl: AutoScanController): ScanController
}
