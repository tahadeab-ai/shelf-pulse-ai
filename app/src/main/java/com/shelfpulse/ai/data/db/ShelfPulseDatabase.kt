package com.shelfpulse.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InventorySessionEntity::class,
        ShelfScanRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ShelfPulseDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: ShelfPulseDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN rawBoxCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN mergedBoxCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN tileCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): ShelfPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShelfPulseDatabase::class.java,
                    "shelfpulse_inventory.db"
                ).addMigrations(MIGRATION_1_2)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
