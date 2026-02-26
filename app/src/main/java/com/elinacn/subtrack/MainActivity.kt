package com.elinacn.subtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.elinacn.subtrack.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}


@Composable
fun MainScreen() {
    Scaffold(
        containerColor = PastelGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ){

            DashboardCard(totalAmount = "458.90 TL")

            Text(
                text = stringResource(id = R.string.my_subscriptions),
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )

            SubscriptionCard(name = "Netflix", price = "159.99 TL")
            SubscriptionCard(name = "Spotify", price = "59.90 TL")
            SubscriptionCard(name = "YouTube Premium", price = "79.99 TL")
        }
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

        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start

        ){

            Text(
                text= stringResource(id = R.string.total_monthly),
                color = DarkText.copy(alpha = 0.7f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
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
    ){
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ){
            Text(text = name, color = DarkText, fontWeight = FontWeight.Medium)
            Text(text = price, color = PastelBlue, fontWeight = FontWeight.Bold)

        //selam askim
        }
    }
}