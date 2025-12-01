package uk.ac.tees.mad.fixit.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.ac.tees.mad.fixit.data.local.FixItDatabase
import uk.ac.tees.mad.fixit.data.local.IssueReportDao
import uk.ac.tees.mad.fixit.domain.repository.AuthRepository
import uk.ac.tees.mad.fixit.domain.repository.ImageUploadRepository
import uk.ac.tees.mad.fixit.domain.repository.IssueRepository
import uk.ac.tees.mad.fixit.domain.repository.LocationRepository
import uk.ac.tees.mad.fixit.domain.repository.ProfileRepository
import uk.ac.tees.mad.fixit.domain.repository.ReportRepository
import uk.ac.tees.mad.fixit.domain.util.NetworkHelper
import uk.ac.tees.mad.fixit.domain.util.SyncManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideFixItDatabase(@ApplicationContext context: Context): FixItDatabase {
        return FixItDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideIssueReportDao(database: FixItDatabase): IssueReportDao {
        return database.issueReportDao()
    }

    @Provides
    @Singleton
    fun provideNetworkHelper(@ApplicationContext context: Context): NetworkHelper {
        return NetworkHelper(context)
    }

    @Provides
    @Singleton
    fun provideSyncManager(): SyncManager {
        return SyncManager()
    }

    @Provides
    @Singleton
    fun provideReportRepository(): ReportRepository {
        return ReportRepository()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository {
        return AuthRepository()
    }

    @Provides
    @Singleton
    fun provideImageUploadRepository(@ApplicationContext context: Context): ImageUploadRepository {
        return ImageUploadRepository(context)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(@ApplicationContext context: Context): LocationRepository {
        return LocationRepository(context)
    }

    @Provides
    @Singleton
    fun provideIssueRepository(
        localDao: IssueReportDao,
        remoteRepository: ReportRepository,
        networkHelper: NetworkHelper,
        syncManager: SyncManager
    ): IssueRepository {
        return IssueRepository(localDao, remoteRepository, networkHelper, syncManager)
    }

    // âœ… NEW: Profile Repository
    @Provides
    @Singleton
    fun provideProfileRepository(@ApplicationContext context: Context): ProfileRepository {
        return ProfileRepository(context)
    }
}