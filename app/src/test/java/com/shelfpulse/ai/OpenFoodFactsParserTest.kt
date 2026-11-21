package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.remote.OpenFoodFactsClient
import org.junit.Test

class OpenFoodFactsParserTest {

    @Test
    fun parseProductJson_validProduct_returnsSuccess() {
        val barcode = "3017620422003"
        val json = """
            {
                "status": 1,
                "status_verbose": "product found",
                "code": "3017620422003",
                "product": {
                    "product_name": "Nutella Hazelnut Spread",
                    "brands": "Ferrero",
                    "quantity": "400 g"
                }
            }
        """.trimIndent()

        val result = OpenFoodFactsClient.parseProductJson(barcode, json)

        assertThat(result.isSuccess).isTrue()
        val product = result.getOrThrow()
        assertThat(product.barcode).isEqualTo(barcode)
        assertThat(product.productName).isEqualTo("Nutella Hazelnut Spread")
        assertThat(product.brand).isEqualTo("Ferrero")
        assertThat(product.quantity).isEqualTo("400 g")
    }

    @Test
    fun parseProductJson_fallbackToEnglishNameWhenProductNameBlank() {
        val barcode = "5449000000996"
        val json = """
            {
                "status": 1,
                "code": "5449000000996",
                "product": {
                    "product_name": "",
                    "product_name_en": "Coca-Cola Original Taste",
                    "brands": "Coca-Cola",
                    "quantity": "330 ml"
                }
            }
        """.trimIndent()

        val result = OpenFoodFactsClient.parseProductJson(barcode, json)

        assertThat(result.isSuccess).isTrue()
        val product = result.getOrThrow()
        assertThat(product.productName).isEqualTo("Coca-Cola Original Taste")
        assertThat(product.brand).isEqualTo("Coca-Cola")
    }

    @Test
    fun parseProductJson_productNotFoundStatusZero_returnsFailure() {
        val barcode = "0000000000000"
        val json = """
            {
                "status": 0,
                "status_verbose": "product not found",
                "code": "0000000000000"
            }
        """.trimIndent()

        val result = OpenFoodFactsClient.parseProductJson(barcode, json)

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(NoSuchElementException::class.java)
        assertThat(exception?.message).contains("Product not found")
    }

    @Test
    fun parseProductJson_missingProductObject_returnsFailure() {
        val barcode = "1234567890123"
        val json = """
            {
                "status": 1,
                "code": "1234567890123"
            }
        """.trimIndent()

        val result = OpenFoodFactsClient.parseProductJson(barcode, json)

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun parseProductJson_malformedJson_returnsFailure() {
        val barcode = "9876543210987"
        val json = "{ bad json string: "

        val result = OpenFoodFactsClient.parseProductJson(barcode, json)

        assertThat(result.isFailure).isTrue()
    }
}
