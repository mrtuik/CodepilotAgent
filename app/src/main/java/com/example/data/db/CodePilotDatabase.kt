package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        FileEntity::class,
        VersionEntity::class,
        ChangeEntity::class,
        AgentRunEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CodePilotDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun fileDao(): FileDao
    abstract fun versionDao(): VersionDao
    abstract fun changeDao(): ChangeDao
    abstract fun agentRunDao(): AgentRunDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: CodePilotDatabase? = null

        fun getDatabase(context: Context): CodePilotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CodePilotDatabase::class.java,
                    "codepilot_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
