package com.shelfpulse.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InventorySessionEntity::class,
        ShelfScanRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ShelfPulseDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: ShelfPulseDatabase? = null

        fun getInstance(context: Context): ShelfPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShelfPulseDatabase::class.java,
                    "shelfpulse_inventory.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
