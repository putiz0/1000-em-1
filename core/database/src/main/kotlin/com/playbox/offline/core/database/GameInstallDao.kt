package com.playbox.offline.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameInstallDao {

    @Query("SELECT * FROM game_install ORDER BY lastUpdatedAt DESC")
    fun observeAll(): Flow<List<GameInstallEntity>>

    @Query("SELECT * FROM game_install WHERE gameId = :gameId")
    fun observe(gameId: String): Flow<GameInstallEntity?>

    @Query("SELECT * FROM game_install WHERE gameId = :gameId")
    suspend fun find(gameId: String): GameInstallEntity?

    @Upsert
    suspend fun upsert(entity: GameInstallEntity)

    @Upsert
    suspend fun upsertAll(entities: List<GameInstallEntity>)

    @Delete
    suspend fun delete(entity: GameInstallEntity)

    @Query("DELETE FROM game_install WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: String)

    @Query("DELETE FROM game_install")
    suspend fun clear()
}
