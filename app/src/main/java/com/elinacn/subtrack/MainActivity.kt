package com.elinacn.subtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import com.elinacn.subtrack.ui.theme.*

// 1. VERİ MODELİ (Double kullanımı hesaplamalar için kritiktir)
data class Subscription(
    val id: Int,
    val name: String,
    val price: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubTrackTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    // DURUM TANIMLARI
    var showBottomSheet by remember { mutableStateOf(false) }
    var subscriptionName by remember { mutableStateOf("") }
    var subscriptionPrice by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    // Dinamik Liste (Tip güvenliği sağlandı)
    val subscriptionList = remember {
        mutableStateListOf(
            Subscription(1, "Netflix", 159.99),
            Subscription(2, "Spotify", 59.90)
        )
    }

    // Toplam Tutar Hesaplama (Daha performanslı ve temiz)
    val totalMonthlyPrice by remember(subscriptionList.size) {
        derivedStateOf {
            subscriptionList.sumOf { it.price }
        }
    }

    Scaffold(
        containerColor = PastelGray,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = PastelBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { paddingValues ->
        // Column yerine LazyColumn: Büyük listelerde performans sağlar
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp) // FAB için boşluk
        ) {
            // Sabit Üst Kısım
            item {
                DashboardCard(
                    totalAmount = String.format(Locale.US, "%.2f TL", totalMonthlyPrice)
                )

                Text(
                    text = stringResource(id = R.string.my_subscriptions),
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            }

            // Dinamik Liste Elemanları
            items(
                items = subscriptionList,
                key = { it.id } // Silme işlemlerinde görsel hataları önleyen kritik nokta
            ) { sub ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            subscriptionList.remove(sub)
                            true
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            Color.Red.copy(alpha = 0.2f)
                        } else Color.Transparent

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(color, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    }
                ) {
                    SubscriptionCard(
                        name = sub.name,
                        price = String.format(Locale.US, "%.2f TL", sub.price)
                    )
                }
            }
        }
    }

    // EKLEME MENÜSÜ
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Yeni Abonelik Ekle", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = subscriptionName,
                    onValueChange = { subscriptionName = it },
                    label = { Text("Abonelik Adı") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = subscriptionPrice,
                    onValueChange = { subscriptionPrice = it },
                    label = { Text("Fiyat (Örn: 159.99)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (subscriptionName.isNotBlank()) {
                            val priceDouble = subscriptionPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
                            subscriptionList.add(
                                Subscription(
                                    id = (subscriptionList.maxOfOrNull { it.id } ?: 0) + 1,
                                    name = subscriptionName,
                                    price = priceDouble
                                )
                            )
                            // Resetle ve Kapat
                            subscriptionName = ""
                            subscriptionPrice = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PastelBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kaydet", color = Color.White)
                }
            }
        }
    }
}

// YARDIMCI BİLEŞENLER

@Composable
fun getIconForSubscription(name: String): ImageVector {
    return when (name.lowercase()) {
        "netflix" -> Icons.Default.PlayArrow
        "spotify" -> Icons.AutoMirrored.Filled.List
        "youtube" -> Icons.Default.PlayArrow
        "icloud", "drive" -> Icons.Default.Cloud
        else -> Icons.Default.Star
    }
}

@Composable
fun DashboardCard(totalAmount: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PastelBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(id = R.string.total_monthly),
                color = DarkText.copy(alpha = 0.7f),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = totalAmount,
                color = DarkText,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun SubscriptionCard(name: String, price: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                getIconForSubscription(name),
                contentDescription = null,
                tint = PastelBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = name, modifier = Modifier.weight(1f), color = DarkText)
            Text(text = price, color = PastelBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SubTrackPreview() {
    SubTrackTheme { MainScreen() }
}