package com.playbox.offline.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GameInstallEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(InstallStatusConverter::class)
abstract class PlayBoxDatabase : RoomDatabase() {
    abstract fun gameInstallDao(): GameInstallDao
}

/**
 * Fábrica/contêiner simples de banco de dados (sem Hilt, para manter
 * o primeiro build enxuto). Uma instância singleton por processo.
 */
object PlayBoxDatabaseProvider {

    @Volatile
    private var instance: PlayBoxDatabase? = null

    fun get(context: Context): PlayBoxDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): PlayBoxDatabase =
        Room.databaseBuilder(context, PlayBoxDatabase::class.java, "playbox.db")
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
}
