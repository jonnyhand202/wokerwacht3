package com.workwatch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.workwatch.entities.GPSTrailPoint
import com.workwatch.entities.HashLeak
import com.workwatch.entities.UserConfig
import com.workwatch.entities.WorkerLogEntry

@Database(
    entities = [WorkerLogEntry::class, UserConfig::class, HashLeak::class, GPSTrailPoint::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workerLogDao(): WorkerLogDao
    abstract fun configDao(): ConfigDao
    abstract fun hashLeakDao(): HashLeakDao
    abstract fun gpsTrailDao(): GPSTrailDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "workwatch_database"
                )
                .fallbackToDestructiveMigration() // For development simplicity with version bumps
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
