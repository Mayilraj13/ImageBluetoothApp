package com.example.imagebluetoothapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }

    private val selectedImages = mutableStateListOf<Uri>()

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                selectedImages.clear()
                selectedImages.addAll(uris)
                Toast.makeText(this, "${uris.size} images selected", Toast.LENGTH_SHORT).show()
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("USB + Bluetooth Image Share") }
                        )
                    }
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        images = selectedImages,
                        onPickImages = { pickImagesLauncher.launch("image/*") },
                        onSendBluetooth = { sendViaBluetooth(selectedImages) },
                        onUsbTransfer = { saveToDownloads(selectedImages) }
                    )
                }
            }
        }
    }

    /** Request required permissions */
    private fun requestAllPermissions() {
        val needed = mutableListOf<String>()
        val perms = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        for (p in perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p)
            }
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    /** Send selected images via Bluetooth (system picker) */
    private fun sendViaBluetooth(uris: List<Uri>) {
        if (uris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }

        try {
            startActivity(Intent.createChooser(intent, "Send via Bluetooth"))
        } catch (e: Exception) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show()
        }
    }

    /** Save selected images to Downloads (with MTP check) */
    private fun saveToDownloads(uris: List<Uri>) {
        if (uris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // ✅ Check if USB is connected
            val usbManager = getSystemService(USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList

            if (deviceList.isEmpty()) {
                Toast.makeText(this, "⚠ USB not connected. Please connect to PC.", Toast.LENGTH_LONG).show()
                return
            }

            // ✅ Save files to Downloads folder
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()

            for (uri in uris) {
                val inputStream = contentResolver.openInputStream(uri)
                val outFile = File(downloads, "shared_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(outFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
            }

            // ✅ Notify user
            Toast.makeText(
                this,
                "✅ Files saved to Downloads. Visible on PC if File Transfer (MTP) is enabled.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            // ❌ If fails → guide user to enable File Transfer (MTP)
            Toast.makeText(this, "⚠ Please enable File Transfer (MTP) mode in USB Preferences", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_MEMORY_CARD_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    images: List<Uri>,
    onPickImages: () -> Unit,
    onSendBluetooth: () -> Unit,
    onUsbTransfer: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = { onPickImages() }, modifier = Modifier.weight(1f)) {
                Text("Pick Images")
            }
            Button(onClick = { onSendBluetooth() }, modifier = Modifier.weight(1f)) {
                Text("Send Bluetooth")
            }
            Button(onClick = { onUsbTransfer() }, modifier = Modifier.weight(1f)) {
                Text("USB Transfer")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (images.isNotEmpty()) {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                items(images.size) { idx ->
                    Image(
                        painter = rememberAsyncImagePainter(images[idx]),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }
        } else {
            Text(
                text = "No images selected",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
