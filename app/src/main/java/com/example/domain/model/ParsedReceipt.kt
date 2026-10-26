package com.example.domain.model

data class ParsedReceipt(
    val amount: Double?,
    val merchant: String?,
    val dateEpoch: Long?,
    val suggestedCategory: String?,
    val note: String?,
    val suggestedTags: String? = null
)
