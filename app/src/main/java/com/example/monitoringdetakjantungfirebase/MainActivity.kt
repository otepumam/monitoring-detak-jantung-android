package com.example.monitoringdetakjantungfirebase

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

object FirebaseHelper {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartRateMonitorTheme()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(auth: FirebaseAuth, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("")}
    var age by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        if (isRegistering) {
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Umur") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (isRegistering) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            val userId = user?.uid

                            val database = Firebase.database
                            val userRef = database.getReference("users").child(userId!!)

                            val userData = mapOf(
                                "name" to name,
                                "age" to age.toIntOrNull()
                            )

                            userRef.setValue(userData)
                                .addOnSuccessListener {
                                    Log.d("Register", "Registrasi dan penyimpanan data berhasil")
                                    auth.signInWithEmailAndPassword(email, password)
                                        .addOnCompleteListener { loginTask ->
                                            if (loginTask.isSuccessful) {
                                                Log.d("Login", "Login berhasil")
                                                onLoginSuccess() // Memanggil fungsi lambda setelah login berhasil
                                            } else {
                                                loginError =
                                                    "Login gagal: ${loginTask.exception?.message}"
                                            }
                                        }
                                }
                                .addOnFailureListener {
                                    Log.e("Register", "Penyimpanan data gagal: ${it.message}")
                                }
                        } else {
                            loginError = "Registrasi gagal: ${task.exception?.message}"
                        }
                    }
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("Login", "Login berhasil")
                            onLoginSuccess() // Memanggil fungsi lambda setelah login berhasil
                        } else {
                            loginError = "Login gagal: ${task.exception?.message}"
                        }
                    }
            }
        }) {
            Text(if (isRegistering) "Registrasi" else "Login")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isRegistering = !isRegistering }) {
            Text(if (isRegistering) "Sudah punya akun? Login" else "Belum punya akun? Daftar")
        }
        if (loginError.isNotEmpty()) {
            Text(loginError, color = Color.Red, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateMonitorScreen(auth: FirebaseAuth, onLogout: () -> Unit, navController: NavController) {
    var heartRate by remember { mutableStateOf(0) }
    var sendTime by remember { mutableStateOf("") }
    var isHeartRateNormal by remember { mutableStateOf(true) }

    // Mendapatkan data BPM dari Firebase
    val database = Firebase.database
    val historyRef = database.getReference("heart_rate_history").child(auth.currentUser!!.uid)

    DisposableEffect(Unit) {val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()) {

                // Mengurutkan data berdasarkan timestamp (key)
                val sortedSnapshot = snapshot.children.sortedByDescending { it.key }
                val latestSnapshot = sortedSnapshot.firstOrNull()
                // Mengambil snapshot pertama setelah diurutkan
                heartRate = latestSnapshot?.child("bpm")?.getValue(Int::class.java) ?: 0
                isHeartRateNormal = isHeartRateWithinNormalRange(heartRate)
            } else {
                heartRate = 0
                isHeartRateNormal = false
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("FirebaseError", "Error: ${error.message}")
        }
    }

        historyRef.addValueEventListener(listener)

        onDispose {
            historyRef.removeEventListener(listener)
        }
    }

    // Mendapatkan data tanggal dan waktu dari Firebase
    LaunchedEffect(key1 = Unit) {
        val database = Firebase.database
        val dateRef = database.getReference("date")
        val timeRef = database.getReference("time")

        dateRef.addValueEventListener(object : ValueEventListener {override fun onDataChange(snapshot: DataSnapshot) {
            val date = snapshot.getValue(String::class.java) ?: ""
            timeRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val time = snapshot.getValue(String::class.java) ?: ""
                    sendTime = "$date $time"
                    Log.d("FirebaseData", "Date and time received: $sendTime")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseError", "Error: ${error.message}")
                }
            })
        }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error: ${error.message}")
            }
        })
    }

    // Menampilkan data di UI
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Monitoring Detak Jantung") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("history") }) {
                Icon(Icons.Filled.History, contentDescription = "History")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HeartRateDisplay(heartRate, isHeartRateNormal, sendTime)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogout) {
                Text("Logout")
            }
        }
    }
}
@Composable
fun HeartRateDisplay(heartRate: Int, isNormal: Boolean, sendTime: String) {
    val heartRateColor = if (isNormal) Color(0xFF4CAF50) else Color(0xFFF44336) // Green or Red
    Column(
        modifier = Modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$heartRate bpm",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = heartRateColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isNormal)"Normal" else "Tidak Normal",
            fontSize = 20.sp,
            color = heartRateColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Waktu Pengiriman: $sendTime",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

fun isHeartRateWithinNormalRange(heartRate: Int): Boolean {
    return heartRate in 60..100
}

fun convertMillisToDateTime(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("Asia/Makassar")
    val resultDate = Date(millis)
    return sdf.format(resultDate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateHistoryScreen(navController: NavController) {
    val historyData = remember { mutableStateListOf<HeartRateData>() }
    LaunchedEffect(key1 = Unit) {
        val database = Firebase.database
        val historyRef = database.getReference("heart_rate_history").child(FirebaseHelper.auth.currentUser!!.uid)
        historyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyData.clear()
                for (childSnapshot in snapshot.children) {
                    val timestamp = childSnapshot.key ?: ""
                    val bpm = childSnapshot.child("bpm").getValue(Int::class.java) ?: 0
                    val time = childSnapshot.child("time").getValue(String::class.java) ?: ""
                    val date = childSnapshot.child("date").getValue(String::class.java) ?: ""
                    historyData.add(HeartRateData(bpm, time, date, timestamp))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error: ${error.message}")
            }
        })
    }

    val deleteAllHeartRateData: () -> Unit = {
        val database = Firebase.database
        val historyRef = database.getReference("heart_rate_history").child(FirebaseHelper.auth.currentUser!!.uid)
        historyRef.removeValue()
            .addOnSuccessListener {
                Log.d("DeleteAllHistory", "Semua data history berhasil dihapus")
            }
            .addOnFailureListener {
                Log.e("DeleteAllHistory", "Gagal menghapus semua data history: ${it.message}")
            }
    }
    val deleteHeartRateData: (String) -> Unit = { timestamp ->
        val database = Firebase.database
        val historyRef = database.getReference("heart_rate_history").child(FirebaseHelper.auth.currentUser!!.uid).child(timestamp)
        historyRef.removeValue()
            .addOnSuccessListener {
                Log.d("DeleteHistory", "Data history berhasil dihapus")
            }
            .addOnFailureListener {
                Log.e("DeleteHistory", "Gagal menghapus data history: ${it.message}")
            }
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Riwayat Detak Jantung") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        )
    }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize
                    ()
            ) {
                items(historyData) { data ->
                    HeartRateHistoryItem(data, onDelete = deleteHeartRateData)
                }
            }
            Button(
                onClick = { deleteAllHeartRateData() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text("Hapus Semua History")
            }
        }
    }
}

@Composable
fun HeartRateHistoryItem(data: HeartRateData, onDelete: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween)
    {
        Text("BPM: ${data.bpm}, Date: ${data.date}, Time: ${data.time}, Timestamp: ${data.timestamp}")
        IconButton(onClick = { onDelete(data.timestamp) }) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

data class HeartRateData(val bpm: Int, val time: String, val date: String, val timestamp: String)

@Composable
fun HeartRateMonitorTheme() {
    val navController = rememberNavController()
    var isLoggedIn by remember { mutableStateOf(FirebaseHelper.auth.currentUser != null) }

    // Gunakan isLoggedIn untuk menampilkan layar yang sesuai
    if (isLoggedIn) {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                HeartRateMonitorScreen(
                    auth = FirebaseHelper.auth,
                    onLogout = { isLoggedIn = false },
                    navController = navController
                )
            }
            composable("history") {
                HeartRateHistoryScreen(navController) }
        }
    } else {
        LoginScreen(auth = FirebaseHelper.auth) { isLoggedIn = true }
    }

    MaterialTheme { }
}
