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
        ShelfScanRecordEntity::class,
        ProductCatalogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ShelfPulseDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao
    abstract fun productCatalogDao(): ProductCatalogDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_catalog (
                        barcode TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        brand TEXT NOT NULL DEFAULT '',
                        expectedStock INTEGER NOT NULL DEFAULT 0,
                        unitPrice REAL NOT NULL DEFAULT 0.0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN barcode TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN productName TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN expectedStock INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN unitPrice REAL DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                database.execSQL("ALTER TABLE inventory_sessions ADD COLUMN lastSyncedAt INTEGER")
            }
        }

        fun getInstance(context: Context): ShelfPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShelfPulseDatabase::class.java,
                    "shelfpulse_inventory.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
