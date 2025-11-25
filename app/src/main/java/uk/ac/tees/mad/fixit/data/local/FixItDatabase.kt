package uk.ac.tees.mad.fixit.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [IssueReportEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    IssueTypeConverters::class,
    ReportStatusConverters::class
)
abstract class FixItDatabase : RoomDatabase() {

    abstract fun issueReportDao(): IssueReportDao

    companion object {
        @Volatile
        private var INSTANCE: FixItDatabase? = null

        fun getInstance(context: Context): FixItDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FixItDatabase::class.java,
                    "fixit_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}