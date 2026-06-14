package com.platescanner.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.platescanner.app.camera.CameraController
import com.platescanner.app.camera.CameraXController
import com.platescanner.app.data.PlateRecordDao
import com.platescanner.app.data.PlateScannerDatabase
import com.platescanner.app.data.SettingsRepository
import com.platescanner.app.export.ExcelExporter
import com.platescanner.app.export.PoiExcelExporter
import com.platescanner.app.network.MiniMaxApi
import com.platescanner.app.network.MiniMaxApiImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring the real (track-2) implementations of the contract
 * interfaces. Replacing a stub with the production class is a one-line change
 * here — no call site updates needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Single DataStore instance for the whole process. */
    private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
        name = SettingsRepository.DATASTORE_NAME,
    )

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlateScannerDatabase =
        Room.databaseBuilder(
            context,
            PlateScannerDatabase::class.java,
            PlateScannerDatabase.DB_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePlateRecordDao(db: PlateScannerDatabase): PlateRecordDao = db.plateRecordDao()

    @Provides
    @Singleton
    fun provideCameraController(): CameraController = CameraXController()

    @Provides
    @Singleton
    fun provideMiniMaxApi(
        settingsRepository: SettingsRepository,
    ): MiniMaxApi = MiniMaxApiImpl(settingsRepository)

    @Provides
    @Singleton
    fun provideExcelExporter(): ExcelExporter = PoiExcelExporter()
}