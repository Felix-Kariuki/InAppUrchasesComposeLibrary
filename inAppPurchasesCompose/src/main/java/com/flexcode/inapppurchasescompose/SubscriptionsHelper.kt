package com.flexcode.inapppurchasescompose

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SubscriptionsHelper(private val context: Context, val productId: String){

    private lateinit var billingClient: BillingClient
    private lateinit var productDetails: ProductDetails
    private lateinit var purchase: Purchase


    private val _productName = MutableStateFlow("")
    val productName = _productName.asStateFlow()

    private val _purchaseDone = MutableStateFlow(false)
    val purchaseDone = _purchaseDone.asStateFlow()

    private val _purchaseStatus = MutableStateFlow("")
    val purchaseStatus = _purchaseStatus.asStateFlow()

    fun setUpBillingPurchases(){
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(
                billingResult: BillingResult
            ) {
                if (billingResult.responseCode ==
                    BillingClient.BillingResponseCode.OK
                ) {
                    _purchaseStatus.value = "Billing Client Connected"
                    Log.i(PURCHASE_STATUS, "Billing Client Connected")
                    queryProduct(productId)
                    reloadPurchase()
                } else {
                    _purchaseStatus.value = "Billing Client Connection Failure"
                    Log.e(PURCHASE_STATUS, "Billing Client Connection Failure")

                }
            }

            override fun onBillingServiceDisconnected() {
                _purchaseStatus.value = "Billing Client Connection Lost"
                Log.e(PURCHASE_STATUS, "Billing Client Connection Lost")
            }
        })
    }

    fun queryProduct(productId: String) {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ImmutableList.of(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(
                            BillingClient.ProductType.SUBS
                        )
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(
            queryProductDetailsParams
        ) { _, productDetailsList ->
            if (productDetailsList.isNotEmpty()) {
                productDetails = productDetailsList[0]
                _productName.value = "Purchase Product: " + productDetails.name
                Log.i(PURCHASE_STATUS,"Purchase Product: ${productDetails.name}")
            } else {
                _purchaseStatus.value = "No Matching Products Found"
                _purchaseDone.value = false
                Log.e(PURCHASE_STATUS,"No Matching Products Found")
            }
        }
    }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK &&
                purchases != null
            ) {
                for (purchase in purchases) {
                    completePurchase(purchase)
                }
            } else if (billingResult.responseCode ==
                BillingClient.BillingResponseCode.USER_CANCELED
            ) {
                _purchaseStatus.value = "Purchase Canceled"
                Log.i(PURCHASE_STATUS, "Purchase Canceled")
            } else {
                _purchaseStatus.value = "Purchase Error"
                Log.e(PURCHASE_STATUS, " Purchase Error!!")
            }
        }

    private fun completePurchase(item: Purchase) {
        purchase = item
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            _purchaseDone.value = false
            _purchaseStatus.value = "Purchase Completed"
            Log.i(PURCHASE_STATUS, "Purchase Completed")
        }
    }

    fun initializePurchase() {
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                ImmutableList.of(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(productDetails.subscriptionOfferDetails?.get(0)?.offerToken.toString())
                        .build()
                )
            )
            .build()

        billingClient.launchBillingFlow(context as Activity, billingFlowParams)
    }

    private fun reloadPurchase() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(
            queryPurchasesParams,
            purchasesListener
        )
    }

    private val purchasesListener =
        PurchasesResponseListener { _, purchases ->
            if (purchases.isNotEmpty()) {
                purchase = purchases.first()
                _purchaseDone.value = false
                _purchaseStatus.value = "Previous Purchase Found"
                Log.i(PURCHASE_STATUS, "Previous Purchase Found")
            } else {
                _purchaseDone.value = true
            }
        }

    companion object {
        const val PURCHASE_STATUS = "PURCHASE_STATUS"
    }

}

