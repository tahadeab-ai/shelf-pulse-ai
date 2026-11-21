package com.shelfpulse.ai

import android.app.Application
import com.shelfpulse.ai.data.db.ShelfPulseDatabase
import com.shelfpulse.ai.data.repository.InventoryRepository
import com.shelfpulse.ai.nativebridge.ShelfPulseNativeEngine

class ShelfPulseApplication : Application() {

    lateinit var database: ShelfPulseDatabase
        private set

    lateinit var repository: InventoryRepository
        private set

    lateinit var nativeEngine: ShelfPulseNativeEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = ShelfPulseDatabase.getInstance(this)
        repository = InventoryRepository(database.inventoryDao(), database.productCatalogDao())
        nativeEngine = ShelfPulseNativeEngine()
    }
}
