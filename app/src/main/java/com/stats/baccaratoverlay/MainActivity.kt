package com.stats.baccaratoverlay

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, AutoRobotOverlayService::class.java).apply {
                putExtra(AutoRobotOverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(AutoRobotOverlayService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            moveTaskToBack(true)
        } else {
            Toast.makeText(
                this,
                "Bạn cần cấp quyền quay màn hình để Robot V4 Pro cào dữ liệu bảng cầu",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }

        val title = TextView(this).apply {
            text = "🤖 AUTOROBOT BACCARAT V4 PRO\n"
            textSize = 18f
        }

        val info = TextView(this).apply {
            text = "• Sửa triệt để lỗi báo nhầm Hòa / Cái: Loại bỏ đoán màu toàn màn hình gây nhiễu từ phỉnh cược và bàn xanh.\n" +
                    "• Cào dữ liệu trực tiếp từ Bảng Cầu (Bead Plate): Kéo khung xanh khớp khít lên Bảng Cầu 6 hàng rồi bấm 'Khóa & Cào Data'.\n" +
                    "• Tự động đồng bộ từng ván mới xuất hiện trên Bảng Cầu theo thời gian thực (100% chính xác theo lịch sử bàn).\n" +
                    "• Thuật toán Ensemble (Big Road + Cầu Bệt + Cầu 1-1 + Cầu 2-2 + Markov 3 tay + Z-Score) tính tỷ lệ thật không báo ảo.\n\n" +
                    "Cách dùng chuẩn nhất:\n" +
                    "1. Bấm nút bên dưới -> Cấp quyền Cửa sổ nổi & Quay màn hình.\n" +
                    "2. Vào bàn chơi, kéo khung xanh và góc ⤡ cho đè khít lên Bảng Cầu 6 hàng.\n" +
                    "3. Bấm '🔒 Khóa & Cào Data' -> Khung tự cho phép bấm xuyên qua để bạn đặt cược bình thường, Robot tự cập nhật từng ván."
            textSize = 14f
        }

        val btnStart = Button(this).apply {
            text = "🚀 KHỞI ĐỘNG ROBOT V4 PRO"
            setOnClickListener { startAutoRobotFlow() }
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(btnStart)
        setContentView(layout)

        requestNotificationPermissionAndroid14()
    }

    private fun requestNotificationPermissionAndroid14() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun startAutoRobotFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Bước 1: Bật quyền 'Hiển thị trên ứng dụng khác'",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
    }
}
