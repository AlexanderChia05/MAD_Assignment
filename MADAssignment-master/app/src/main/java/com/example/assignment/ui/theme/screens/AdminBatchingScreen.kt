package com.example.assignment.ui.theme.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.assignment.data.Purchase
import com.example.assignment.viewmodel.AdminBatchingViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

private data class SoldLine(
    val productName: String,
    val category: String,
    val unitPrice: Double,
    val quantity: Int
)

private fun commitOrderToSales(
    items: List<SoldLine>,
    buyerName: String = "Unknown"
) {
    val db = FirebaseFirestore.getInstance()
    val ts = System.currentTimeMillis()
    items.forEach { line ->
        val total = line.unitPrice * line.quantity
        val data = hashMapOf(
            "productName" to line.productName,
            "category" to (line.category.ifBlank { "Uncategorized" }),
            "unitPrice" to line.unitPrice,
            "quantity" to line.quantity,
            "timestamp" to ts,            // epoch millis
            "buyerName" to buyerName,
            "total" to total
        )
        db.collection("sales").add(data)
            .addOnFailureListener { e -> Log.e("Batching", "Failed to log sale: ${e.message}", e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AdminBatchingScreen(navController: NavHostController, category: String? = null) {
    val batchingViewModel: AdminBatchingViewModel = viewModel()
    val context = LocalContext.current

    // Decode category to handle URL-encoded strings
    val decodedCategory = category?.let { URLDecoder.decode(it, "UTF-8") }
    LaunchedEffect(decodedCategory) { Log.d("AdminBatchingScreen", "Received category: $decodedCategory") }

    // ----- ViewModel state (already persists via SavedStateHandle in your VM) -----
    val purchasedItems by batchingViewModel.purchasedItems
    val filteredItems by batchingViewModel.filteredItems
    val errorMessage by batchingViewModel.errorMessage
    val isLoading by batchingViewModel.isLoading
    val showSearchDialog by batchingViewModel.showSearchDialog
    val showCameraDialog by batchingViewModel.showCameraDialog
    val showProductActionDialog by batchingViewModel.showProductActionDialog
    val showRemoveDialog by batchingViewModel.showRemoveDialog
    val showUpdateDialog by batchingViewModel.showUpdateDialog
    val searchQuery by batchingViewModel.searchQuery
    val searchField by batchingViewModel.searchField
    val scannedCode by batchingViewModel.scannedCode
    val selectedPurchase by batchingViewModel.selectedPurchase
    val removeQuantity by batchingViewModel.removeQuantity
    val removeReason by batchingViewModel.removeReason
    val updateProductName by batchingViewModel.updateProductName
    val updateSerialNumber by batchingViewModel.updateSerialNumber
    val updateCategory by batchingViewModel.updateCategory
    val updateSize by batchingViewModel.updateSize

    // ----- Sell dialog state (now survives rotation) -----
    batchingViewModel.setShowSellDialog(true)
    batchingViewModel.setSellUnitText("...")
    batchingViewModel.setSellQtyText("...")

    val showSellDialog by batchingViewModel.showSellDialog
    val sellUnitText   by batchingViewModel.sellUnitText
    val sellQtyText    by batchingViewModel.sellQtyText

    // Firebase Auth state
    val currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var showAuthDialog by rememberSaveable { mutableStateOf(currentUser == null) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            batchingViewModel.onCameraClicked()
        } else {
            Toast.makeText(context, "Camera permission is required for barcode scanning", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            batchingViewModel.fetchPurchasedItems(decodedCategory)
        } else {
            showAuthDialog = true
        }
    }

    // Authentication dialog for non-authenticated users
    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text("Authentication Required") },
            text = { Text("You must be logged in to access this screen.") },
            confirmButton = {
                Button(onClick = { navController.popBackStack() }) { Text("OK") }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batching${decodedCategory?.let { " - $it" } ?: ""}") },
                actions = {
                    IconButton(onClick = { batchingViewModel.onSearchClicked() }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                            PackageManager.PERMISSION_GRANTED -> batchingViewModel.onCameraClicked()
                            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Screen scroll owner
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Purchased Products for Batching${decodedCategory?.let { " ($it)" } ?: ""}:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val itemsToShow = if (searchQuery.isNotEmpty() || scannedCode.isNotEmpty()) {
                        filteredItems
                    } else {
                        purchasedItems
                    }

                    if (itemsToShow.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = "Search Results for: \"$searchQuery\" in $searchField",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(
                            onClick = { batchingViewModel.clearFilters() },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) { Text("Clear Filters") }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isNotEmpty() || scannedCode.isNotEmpty()) {
                                    "No items found matching your search criteria."
                                } else {
                                    "No purchased items available for batching${decodedCategory?.let { " in $it" } ?: ""}."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        if (searchQuery.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(
                                    text = "Search Results for: \"$searchQuery\" in $searchField",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (scannedCode.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Text(
                                    text = "Scanned Code: \"$scannedCode\"",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (searchQuery.isNotEmpty() || scannedCode.isNotEmpty()) {
                            TextButton(
                                onClick = { batchingViewModel.clearFilters() },
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) { Text("Clear Filters") }
                        }

                        // Non-scrolling item cards (parent Column scrolls)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (purchase in itemsToShow) {
                                PurchasedItemCard(
                                    purchase = purchase,
                                    onItemClick = {
                                        // Remember which product we’re working on
                                        batchingViewModel.setSellProductId(it.id)

                                        // Only auto-fill if user hasn't typed anything yet
                                        if (sellUnitText.isEmpty() || sellQtyText.isEmpty()) {
                                            val baseUnit = if (it.quantity > 0) it.price else 0.0
                                            batchingViewModel.setSellUnitText(String.format("%.2f", baseUnit * 2)) // suggestion
                                            batchingViewModel.setSellQtyText(if (it.quantity > 0) "1" else "0")
                                        }
                                        batchingViewModel.onItemClicked(it)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showSearchDialog) {
                SearchDialog(
                    searchQuery = searchQuery,
                    searchField = searchField,
                    filteredItems = filteredItems,
                    onSearchQueryChange = { batchingViewModel.updateSearchQuery(it) },
                    onSearchFieldChange = { batchingViewModel.updateSearchField(it) },
                    onSearch = { batchingViewModel.performSearch() },
                    onDismiss = { batchingViewModel.onDismissSearch() },
                    onSelectItem = { purchase ->
                        // Prime default Sell values when selecting from search
                        if (sellUnitText.isEmpty() || sellQtyText.isEmpty()) {
                            val baseUnit = if (purchase.quantity > 0) purchase.price else 0.0
                            batchingViewModel.setSellUnitText(String.format("%.2f", baseUnit * 2))
                            batchingViewModel.setSellQtyText(if (purchase.quantity > 0) "1" else "0")
                        }
                        batchingViewModel.onItemClicked(purchase)
                        batchingViewModel.onDismissSearch()
                    }
                )
            }

            if (showCameraDialog) {
                CameraDialog(
                    onBarcodeScanned = { barcode ->
                        batchingViewModel.processBarcodeResult(barcode)
                    },
                    onDismiss = { batchingViewModel.onDismissCamera() }
                )
            }

            // Product actions (with new "Sell Product" at the top)
            if (showProductActionDialog) {
                ProductActionDialog(
                    purchase = selectedPurchase,
                    onSellProduct = {
                        selectedPurchase?.let { p ->
                            batchingViewModel.setSellProductId(p.id)
                            if (sellUnitText.isEmpty() || sellQtyText.isEmpty()) {
                                val base = if (p.quantity > 0) p.price else 0.0
                                batchingViewModel.setSellUnitText(String.format("%.2f", base * 2))
                                batchingViewModel.setSellQtyText(if (p.quantity > 0) "1" else "0")
                            }
                        }
                        batchingViewModel.setShowSellDialog(true)
                    },
                    onRemoveProduct = { batchingViewModel.onRemoveProductClicked() },
                    onUpdateInfo = { batchingViewModel.onUpdateInfoClicked() },
                    onDismiss = { batchingViewModel.onDismissProductAction() }
                )
            }

            // Controlled Sell dialog (survives rotation)
            val sel = selectedPurchase
            if (showSellDialog && sel != null) {
                SellProductDialog(
                    purchase = sel,
                    sellUnitText = sellUnitText,
                    onSellUnitTextChange = { new ->
                        if (new.isEmpty() || new.matches(Regex("""\d*\.?\d*""")))
                            batchingViewModel.setSellUnitText(new)
                    },
                    sellQtyText = sellQtyText,
                    onSellQtyTextChange = { new ->
                        if (new.isEmpty() || new.all { it.isDigit() })
                            batchingViewModel.setSellQtyText(new)
                    },
                    onConfirm = { qty, unitPrice ->
                        val buyerName = currentUser?.displayName ?: currentUser?.email ?: "Walk-in"
                        batchingViewModel.sellProduct(
                            purchase = sel,
                            sellQty = qty,
                            sellUnitPrice = unitPrice,
                            buyerName = buyerName
                        ) { success, msg ->
                            Toast.makeText(
                                context,
                                msg ?: if (success) "Sold $qty × ${sel.productName}" else "Sell failed",
                                Toast.LENGTH_SHORT
                            ).show()

                            if (success) {
                                batchingViewModel.setShowSellDialog(false)
                                batchingViewModel.onDismissProductAction()
                                batchingViewModel.fetchPurchasedItems(decodedCategory)
                            }
                        }
                    },
                    onDismiss = {
                        // Only close the Sell dialog; keep other dialogs unchanged
                        batchingViewModel.setShowSellDialog(false)
                    }
                )
            }

            if (showRemoveDialog) {
                RemoveProductDialog(
                    purchase = selectedPurchase,
                    removeQuantity = removeQuantity,
                    removeReason = removeReason,
                    onQuantityChange = { batchingViewModel.updateRemoveQuantity(it) },
                    onReasonChange = { batchingViewModel.updateRemoveReason(it) },
                    onConfirm = {
                        batchingViewModel.removeProduct { success, message ->
                            Toast.makeText(
                                context,
                                message ?: if (success) "Product removed successfully" else "Failed to remove product",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDismiss = { batchingViewModel.onDismissRemove() }
                )
            }

            if (showUpdateDialog) {
                UpdateProductDialog(
                    productName = updateProductName,
                    serialNumber = updateSerialNumber,
                    category = updateCategory,
                    size = updateSize,
                    onProductNameChange = { batchingViewModel.updateProductName(it) },
                    onSerialNumberChange = { batchingViewModel.updateSerialNumber(it) },
                    onCategoryChange = { batchingViewModel.updateCategory(it) },
                    onSizeChange = { batchingViewModel.updateSize(it) },
                    onConfirm = {
                        batchingViewModel.updateProductInfo { success, message ->
                            Toast.makeText(
                                context,
                                message ?: if (success) "Product updated successfully" else "Failed to update product",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDismiss = { batchingViewModel.onDismissUpdate() }
                )
            }
        }
    }
}

@Composable
fun SearchDialog(
    searchQuery: String,
    searchField: String,
    filteredItems: List<Purchase>,
    onSearchQueryChange: (String) -> Unit,
    onSearchFieldChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    onSelectItem: (Purchase) -> Unit
) {
    val focusManager = LocalFocusManager.current
    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            onDismiss()
        },
        title = { Text("Search Products") },
        text = {
            Column {
                Text("Select search field and enter term:")
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Name", "S.Num", "Size", "Cate.").forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                            onClick = { onSearchFieldChange(label) },
                            selected = searchField == label
                        ) {
                            Text(text = label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        onSearchQueryChange(query)
                        onSearch()
                    },
                    label = { Text("Search $searchField") },
                    placeholder = { Text("Enter $searchField") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onSearch()
                        }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredItems.isNotEmpty()) {
                    Text(
                        text = "Search Results:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .fillMaxWidth()
                    ) {
                        items(filteredItems) { purchase: Purchase ->
                            Text(
                                text = purchase.productName.ifEmpty { "Unknown Product" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectItem(purchase) }
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(onClick = onSearch) { Text("Search") } }
    )
}

@Composable
fun CameraDialog(
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Barcode") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder()
                                    .setTargetResolution(android.util.Size(1280, 720))
                                    .build()
                                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setTargetResolution(android.util.Size(1280, 720))
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                                            processImageForBarcode(imageProxy, onBarcodeScanned) {
                                                onDismiss()
                                            }
                                        }
                                    }

                                val cameraSelector = CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                    .build()

                                cameraProvider.unbindAll()
                                try {
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraDialog", "Failed to bind camera: ${e.message}", e)
                                    Toast.makeText(context, "Camera binding failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                Log.e("CameraDialog", "Failed to initialize camera: ${e.message}", e)
                                Toast.makeText(context, "Failed to initialize camera: ${e.message}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Point camera at barcode/QR code",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp)
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {}
    )

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }
}

@OptIn(ExperimentalGetImage::class)
@SuppressLint("UnsafeOptInUsageError")
private fun processImageForBarcode(
    imageProxy: ImageProxy,
    onBarcodeScanned: (String) -> Unit,
    onScanComplete: () -> Unit
) {
    val mediaImage = imageProxy.image ?: return imageProxy.close()
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { barcode ->
                barcode.valueType in listOf(Barcode.TYPE_TEXT, Barcode.TYPE_URL, Barcode.TYPE_PRODUCT)
            }?.rawValue?.let { value ->
                onBarcodeScanned(value)
                onScanComplete()
            }
        }
        .addOnFailureListener { e -> Log.e("CameraDialog", "Barcode scanning failed: ${e.message}") }
        .addOnCompleteListener { imageProxy.close() }
}

/** PRODUCT ACTIONS DIALOG — with Sell Product added above Remove */
@Composable
fun ProductActionDialog(
    purchase: Purchase?,
    onSellProduct: () -> Unit,
    onRemoveProduct: () -> Unit,
    onUpdateInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    if (purchase == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Product Actions") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // --- QR Code (based on serial if present else purchase id)
                val qrContent = purchase.serialNumber?.takeIf { it.isNotEmpty() } ?: purchase.id
                val qrBitmap = remember { generateQRCode(qrContent) }
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code for $qrContent",
                        modifier = Modifier
                            .size(150.dp)
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                    )
                }

                // --- Header / IDs / Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = purchase.productName.ifEmpty { "Unknown Product" },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Order ID: ${purchase.orderId.take(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Purchase ID: ${purchase.id.take(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(purchase.purchaseDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Full product details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!purchase.serialNumber.isNullOrEmpty()) {
                            Text(
                                text = "Serial: ${purchase.serialNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text("Category: ${purchase.category}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                        Text("Size: ${purchase.size}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                        Text("Quantity: ${purchase.quantity}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
                        Text("Total Cost: ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("$${String.format("%.2f", purchase.totalPrice)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        if (purchase.isAdminPurchase) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "Admin Purchase",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("What would you like to do?")
            }
        },
        dismissButton = {},
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = onSellProduct, modifier = Modifier.fillMaxWidth()) { Text("Sell Product") }
                Button(
                    onClick = onRemoveProduct,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Remove Product") }
                Button(onClick = onUpdateInfo, modifier = Modifier.fillMaxWidth()) { Text("Update Info") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    )
}

/** SELL PRODUCT DIALOG — now controlled inputs so values survive rotation via rememberSaveable */
@Composable
fun SellProductDialog(
    purchase: Purchase,
    sellUnitText: String,
    onSellUnitTextChange: (String) -> Unit,
    sellQtyText: String,
    onSellQtyTextChange: (String) -> Unit,
    onConfirm: (quantity: Int, sellUnitPrice: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val purchasedUnit = remember(purchase.quantity, purchase.price) {
        if (purchase.quantity > 0) purchase.price else 0.0
    }

    val sellUnit = sellUnitText.toDoubleOrNull() ?: 0.0
    val sellQty = sellQtyText.toIntOrNull() ?: 0
    val total = sellUnit * sellQty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell Product") },
        text = {
            Column (modifier = Modifier.verticalScroll(rememberScrollState()))
            {
                Text("Product: ${purchase.productName}")
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = String.format("$%.2f / unit", purchasedUnit),
                    onValueChange = {},
                    label = { Text("Purchased Unit Price") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sellUnitText,
                    onValueChange = onSellUnitTextChange,
                    label = { Text("Sell Unit Price (suggested 2×)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sellQtyText,
                    onValueChange = onSellQtyTextChange,
                    label = { Text("Sell Quantity") },
                    supportingText = { Text("Available: ${purchase.quantity}") },
                    isError = sellQty > purchase.quantity,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Total: $${"%.2f".format(total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                enabled = sellUnit > 0 && sellQty in 1..purchase.quantity,
                onClick = { onConfirm(sellQty, sellUnit) }
            ) { Text("Confirm Sell") }
        }
    )
}

fun generateQRCode(text: String, size: Int = 512): Bitmap? {
    return try {
        if (text.isEmpty()) return null
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        Log.e("QRCodeGenerator", "Failed to generate QR code: ${e.message}")
        null
    }
}

@Composable
fun RemoveProductDialog(
    purchase: Purchase?,
    removeQuantity: String,
    removeReason: String,
    onQuantityChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (purchase == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Product") },
        text = {
            Column (modifier = Modifier.verticalScroll(rememberScrollState()))
            {
                Text("Product: ${purchase.productName}")
                Text("Available Quantity: ${purchase.quantity}")
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = removeQuantity,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } || newValue.isEmpty()) onQuantityChange(newValue)
                    },
                    label = { Text("Quantity to Remove") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = removeQuantity.isNotEmpty() && removeQuantity.toIntOrNull() == null
                )
                if (removeQuantity.isNotEmpty() && removeQuantity.toIntOrNull() == null) {
                    Text(
                        text = "Please enter a valid number",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = removeReason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason (Optional)") },
                    placeholder = { Text("e.g., Damaged, Broken, etc.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = removeQuantity.isNotEmpty() &&
                        removeQuantity.toIntOrNull() != null &&
                        removeQuantity.toInt() > 0 &&
                        purchase != null &&
                        removeQuantity.toInt() <= purchase.quantity,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Remove") }
        }
    )
}

@Composable
fun UpdateProductDialog(
    productName: String,
    serialNumber: String,
    category: String,
    size: String,
    onProductNameChange: (String) -> Unit,
    onSerialNumberChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSizeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Product Information") },
        text = {
            Column (modifier = Modifier.verticalScroll(rememberScrollState()))
            {
                OutlinedTextField(
                    value = productName,
                    onValueChange = onProductNameChange,
                    label = { Text("Product Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = productName.isEmpty()
                )
                if (productName.isEmpty()) {
                    Text(
                        text = "Product name is required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = onSerialNumberChange,
                    label = { Text("Serial Number") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = onCategoryChange,
                    label = { Text("Category *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = category.isEmpty()
                )
                if (category.isEmpty()) {
                    Text(
                        text = "Category is required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = size,
                    onValueChange = onSizeChange,
                    label = { Text("Size *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = size.isEmpty()
                )
                if (size.isEmpty()) {
                    Text(
                        text = "Size is required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = productName.isNotEmpty() && category.isNotEmpty() && size.isNotEmpty()
            ) { Text("Update") }
        }
    )
}

@Composable
fun PurchasedItemCard(
    purchase: Purchase,
    onItemClick: (Purchase) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(purchase) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = purchase.productName.ifEmpty { "Unknown Product" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Order ID: ${purchase.orderId.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Purchase ID: ${purchase.id.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(java.util.Date(purchase.purchaseDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!purchase.serialNumber.isNullOrEmpty()) {
                        Text(
                            text = "Serial: ${purchase.serialNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Text("Category: ${purchase.category}", style = MaterialTheme.typography.bodyMedium)
                    Text("Size: ${purchase.size}", style = MaterialTheme.typography.bodyMedium)
                    Text("Quantity: ${purchase.quantity}", style = MaterialTheme.typography.bodyMedium)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Cost:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$${String.format("%.2f", purchase.totalPrice)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (purchase.isAdminPurchase) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Admin Purchase",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
