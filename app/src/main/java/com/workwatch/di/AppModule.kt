package com.workwatch.di

import android.content.Context
import com.google.gson.Gson
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.workwatch.data.*
import com.workwatch.firebase.FirestoreServiceImpl
import com.workwatch.manager.AdminReportProcessor
import com.workwatch.manager.WorkerCheckManager
import com.workwatch.p2p.AdminP2PServer
import com.workwatch.reporting.ReportSender
import com.workwatch.reporting.WorkerLeakSender
import com.workwatch.reporting.WorkerReportAnalyzer
import com.workwatch.security.CryptoUtils
import com.workwatch.security.KeyStoreManager
import com.workwatch.validation.MasterValidator
import com.workwatch.tracking.GPSTrailManager
import com.workwatch.tracking.GeofenceManager
import com.workwatch.reporting.DailyReportGenerator
import com.workwatch.reporting.MonthlyExporter
import com.workwatch.security.TamperDetection
import com.workwatch.sharing.ManualShareManager
import com.workwatch.sharing.TelegramBotService
import com.workwatch.security.TelephonyCollector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideWorkerLogDao(db: AppDatabase): WorkerLogDao = db.workerLogDao()

    @Provides
    @Singleton
    fun provideConfigDao(db: AppDatabase): ConfigDao = db.configDao()

    @Provides
    @Singleton
    fun provideHashLeakDao(db: AppDatabase): HashLeakDao = db.hashLeakDao()

    @Provides
    @Singleton
    fun provideGPSTrailDao(db: AppDatabase): GPSTrailDao = db.gpsTrailDao()

    @Provides
    @Singleton
    fun provideWorkerRepository(
        logDao: WorkerLogDao,
        configDao: ConfigDao,
        gpsTrailDao: GPSTrailDao
    ): WorkerRepository =
        WorkerRepository(logDao, configDao, gpsTrailDao)

    @Provides
    @Singleton
    fun provideCryptoUtils(): CryptoUtils = CryptoUtils()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideKeyStoreManager(): KeyStoreManager = KeyStoreManager()

    @Provides
    @Singleton
    fun provideMasterValidator(cryptoUtils: CryptoUtils): MasterValidator =
        MasterValidator(cryptoUtils)

    @Provides
    @Singleton
    fun provideTelephonyCollector(): TelephonyCollector = TelephonyCollector()

    @Provides
    @Singleton
    fun provideReportSender(
        cryptoUtils: CryptoUtils,
        gson: Gson,
        telephonyCollector: TelephonyCollector
    ): ReportSender =
        ReportSender(cryptoUtils, gson, telephonyCollector)

    @Provides
    @Singleton
    fun provideWorkerLeakSender(
        cryptoUtils: CryptoUtils,
        firestoreService: FirestoreServiceImpl
    ): WorkerLeakSender =
        WorkerLeakSender(cryptoUtils, firestoreService)

    @Provides
    @Singleton
    fun provideWorkerReportAnalyzer(cryptoUtils: CryptoUtils, logDao: WorkerLogDao, gson: Gson): WorkerReportAnalyzer =
        WorkerReportAnalyzer(cryptoUtils, logDao, gson)

    @Provides
    @Singleton
    fun provideWorkerCheckManager(
        validator: MasterValidator,
        reportSender: ReportSender,
        leakSender: WorkerLeakSender,
        keyStoreManager: KeyStoreManager,
        repository: WorkerRepository
    ): WorkerCheckManager = WorkerCheckManager(validator, reportSender, leakSender, keyStoreManager, repository)

    @Provides
    @Singleton
    fun provideAdminReportProcessor(
        cryptoUtils: CryptoUtils,
        keyStoreManager: KeyStoreManager,
        gson: Gson
    ): AdminReportProcessor = AdminReportProcessor(cryptoUtils, keyStoreManager, gson)

    @Provides
    @Singleton
    fun provideAdminP2PServer(leakDao: HashLeakDao, cryptoUtils: CryptoUtils): AdminP2PServer =
        AdminP2PServer(leakDao, cryptoUtils)

    @Provides
    @Singleton
    fun provideGPSTrailManager(repository: WorkerRepository): GPSTrailManager =
        GPSTrailManager(repository)

    @Provides
    @Singleton
    fun provideGeofenceManager(): GeofenceManager =
        GeofenceManager()

    @Provides
    @Singleton
    fun provideDailyReportGenerator(
        repository: WorkerRepository,
        cryptoUtils: CryptoUtils,
        gson: Gson
    ): DailyReportGenerator =
        DailyReportGenerator(repository, cryptoUtils, gson)

    @Provides
    @Singleton
    fun provideMonthlyExporter(
        repository: WorkerRepository,
        gson: Gson
    ): MonthlyExporter =
        MonthlyExporter(repository, gson)

    @Provides
    @Singleton
    fun provideTamperDetection(gson: Gson): TamperDetection =
        TamperDetection(gson)

    @Provides
    @Singleton
    fun provideManualShareManager(): ManualShareManager =
        ManualShareManager()

    @Provides
    @Singleton
    fun provideTelegramBotService(): TelegramBotService =
        TelegramBotService()

    // Firebase Services

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirestoreService(firestore: FirebaseFirestore): FirestoreServiceImpl =
        FirestoreServiceImpl(firestore)

}
