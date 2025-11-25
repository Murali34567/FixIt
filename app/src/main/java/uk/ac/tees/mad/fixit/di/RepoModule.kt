package uk.ac.tees.mad.fixit.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.ac.tees.mad.fixit.data.local.FixItDatabase
import uk.ac.tees.mad.fixit.data.local.IssueReportDao
import uk.ac.tees.mad.fixit.domain.repository.IssueRepository
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
    fun provideIssueRepository(
        localDao: IssueReportDao,
        remoteRepository: ReportRepository,
        networkHelper: NetworkHelper,
        syncManager: SyncManager
    ): IssueRepository {
        return IssueRepository(localDao, remoteRepository, networkHelper, syncManager)
    }
}