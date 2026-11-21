package com.shelfpulse.ai.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductCatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductCatalogEntity)

    @Query("SELECT * FROM product_catalog WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductCatalogEntity?

    @Query("SELECT * FROM product_catalog WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<ProductCatalogEntity>

    @Query("SELECT * FROM product_catalog ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<ProductCatalogEntity>>

    @Query("DELETE FROM product_catalog WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}
