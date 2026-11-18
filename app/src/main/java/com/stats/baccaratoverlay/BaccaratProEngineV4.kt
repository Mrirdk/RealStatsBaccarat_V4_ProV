package com.stats.baccaratoverlay

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class V4ProReport(
    val sideMainText: String,
    val sideSubText: String,
    val sideBridgeTag: String,
    val recommendedSide: String, // "P", "B", or "WAIT"
    val lastEventText: String,
    val bridgeDetailText: String,
    val winLossText: String,
    val historyTapeText: String
)

object BaccaratProEngineV4 {

    // Ngưỡng tin cậy thực tế sau khi cộng hưởng đa thuật toán (cầu 50/50 nhiễu sẽ bị lọc bỏ)
    private const val ENTRY_THRESHOLD = 57.5

    fun analyze(
        rawHistory: List<String>,
        toolWins: Int,
        toolLosses: Int,
        sourceTag: String
    ): V4ProReport {
        val total = rawHistory.size
        val winTotal = toolWins + toolLosses
        val accBadge = if (winTotal > 0) {
            val pct = (toolWins.toDouble() / winTotal) * 100.0
            String.format(Locale.US, "%dW-%dL (%.0f%%)", toolWins, toolLosses, pct)
        } else {
            "Chưa vào lệnh"
        }

        val winLossLine = if (winTotal > 0) {
            val pct = (toolWins.toDouble() / winTotal) * 100.0
            String.format(Locale.US, "Hiệu suất chốt thật: %dW - %dL (%.1f%%)", toolWins, toolLosses, pct)
        } else {
            "Hiệu suất chốt thật: 0W - 0L (Đang theo dõi nhịp cầu)"
        }

        if (total == 0) {
            return V4ProReport(
                sideMainText = "🎯 KHÓA KHUNG BẢNG CẦU",
                sideSubText = "Thật: P 0 (0%) | B 0 (0%) | T 0",
                sideBridgeTag = "Đặt khung xanh lên Bảng Cầu rồi bấm Khóa",
                recommendedSide = "WAIT",
                lastEventText = "Trạng thái: Chưa có dữ liệu bàn",
                bridgeDetailText = "Phân tích cầu thật: Kéo khung xanh đè khít lên Bảng Cầu để cào toàn bộ ván",
                winLossText = winLossLine,
                historyTapeText = "Chuỗi 16 tay cuối: Trống"
            )
        }

        val pCount = rawHistory.count { it == "P" }
        val bCount = rawHistory.count { it == "B" }
        val tCount = rawHistory.count { it == "T" }

        val pPct = (pCount.toDouble() / total) * 100.0
        val bPct = (bCount.toDouble() / total) * 100.0

        val subText = String.format(
            Locale.US,
            "Thật: P %d (%.0f%%) | B %d (%.0f%%) | T %d",
            pCount, pPct, bCount, bPct, tCount
        )

        val lastRaw = rawHistory.last()
        val lastLabel = when (lastRaw) {
            "P" -> "🔵 CON (PLAYER)"
            "B" -> "🔴 CÁI (BANKER)"
            else -> "🟢 HÒA (TIE)"
        }
        val lastEventLine = "Vừa cào ($total ván): $lastLabel [$sourceTag]"
        val tapeLine = "Chuỗi (${rawHistory.size} tay): ${rawHistory.takeLast(16).joinToString("-")}"

        // Lọc chuỗi P/B (Hòa giữ nguyên nhịp cầu đang chạy)
        val pb = rawHistory.filter { it == "P" || it == "B" }
        val n = pb.size

        if (n < 4) {
            return V4ProReport(
                sideMainText = "⏳ CÀO DATA ($n/4 TAY)",
                sideSubText = subText,
                sideBridgeTag = "Vừa ra: $lastRaw • Cần tối thiểu 4 tay P/B",
                recommendedSide = "WAIT",
                lastEventText = lastEventLine,
                bridgeDetailText = "Phân tích cầu thật: Đang thu thập đủ 4 tay P/B để tính nhịp cầu chuẩn",
                winLossText = winLossLine,
                historyTapeText = tapeLine
            )
        }

        // --- MÔ HÌNH ĐA TẦNG (ENSEMBLE) TỪ DỮ LIỆU THỰC CỦA BÀN ---
        // 1. Phân tích cấu trúc Big Road (Chuỗi các cột bệt liên tiếp: VD [3, 1, 2, 1, 3])
        val columns = mutableListOf<Pair<String, Int>>()
        var curSide = pb[0]
        var curLen = 1
        for (i in 1 until n) {
            if (pb[i] == curSide) {
                curLen++
            } else {
                columns.add(curSide to curLen)
                curSide = pb[i]
                curLen = 1
            }
        }
        columns.add(curSide to curLen)

        val lastCol = columns.last()
        val currentSide = lastCol.first
        val currentStreak = lastCol.second
        val oppositeSide = if (currentSide == "P") "B" else "P"

        // Tính điểm bình chọn cho P và B từ các cầu thực tế đã hình thành trong bàn
        var scoreP = 0.0
        var scoreB = 0.0
        val reasons = mutableListOf<String>()

        // Tầng A: Nhận diện Cầu Bệt (Streak >= 3) & Khả năng bệt tiếp trong chính hộp bài này
        if (currentStreak >= 3) {
            val pastStreaksSame = columns.dropLast(1).filter { it.first == currentSide && it.second >= 3 }
            val continuedCount = pastStreaksSame.count { it.second > currentStreak }
            val totalPast = pastStreaksSame.size
            val streakProb = if (totalPast >= 1) {
                (continuedCount + 1.5) / (totalPast + 2.5)
            } else {
                0.63
            }
            if (currentSide == "P") scoreP += streakProb * 3.0 else scoreB += streakProb * 3.0
            reasons.add("Bệt ${if (currentSide == "P") "CON" else "CÁI"} dây $currentStreak")
        }

        // Tầng B: Nhận diện Cầu 1-1 (Ping-Pong: ít nhất 4 cột liên tiếp có độ dài = 1)
        var singleColRun = 0
        for (i in columns.indices.reversed()) {
            if (columns[i].second == 1) singleColRun++ else break
        }
        if (singleColRun >= 4) {
            if (oppositeSide == "P") scoreP += 2.6 else scoreB += 2.6
            reasons.add("Cầu 1-1 ($singleColRun nhịp)")
        }

        // Tầng C: Nhận diện Cầu 2-2 (ít nhất 3 cột gần nhất đều có độ dài = 2 hoặc đang ở nhịp 1 của cặp 2-2)
        if (columns.size >= 3) {
            val c1 = columns[columns.size - 3].second
            val c2 = columns[columns.size - 2].second
            val c3 = columns[columns.size - 1].second
            if (c1 == 2 && c2 == 2 && c3 == 1) {
                // Đang đi nhịp 1 của cột thứ 3 trong cầu 2-2 -> đánh theo nhịp 2
                if (currentSide == "P") scoreP += 2.4 else scoreB += 2.4
                reasons.add("Cầu 2-2 (đang lên tay 2)")
            } else if (c1 == 2 && c2 == 2 && c3 == 2) {
                // Đã đủ 2 tay của cột thứ 3 trong cầu 2-2 -> đảo chiều
                if (oppositeSide == "P") scoreP += 2.3 else scoreB += 2.3
                reasons.add("Cầu 2-2 (đủ cặp -> bẻ)")
            }
        }

        // Tầng D: Khớp mẫu Markov bậc 3 (3 tay gần nhất) trong lịch sử bàn
        if (n >= 5) {
            val k1 = pb[n - 3]
            val k2 = pb[n - 2]
            val k3 = pb[n - 1]
            var m3P = 0
            var m3B = 0
            for (i in 0 until n - 3) {
                if (pb[i] == k1 && pb[i + 1] == k2 && pb[i + 2] == k3) {
                    if (pb[i + 3] == "P") m3P++ else m3B++
                }
            }
            val m3Total = m3P + m3B
            if (m3Total >= 2) {
                val pRate = (m3P + 1.0) / (m3Total + 2.0)
                val bRate = (m3B + 1.0) / (m3Total + 2.0)
                scoreP += pRate * 2.5
                scoreB += bRate * 2.5
                if (m3P != m3B) {
                    reasons.add("Mẫu 3 tay (${max(m3P, m3B)}/$m3Total)")
                }
            }
        }

        // Tầng E: Khớp mẫu Markov bậc 2 (2 tay gần nhất) trong lịch sử bàn
        val j1 = pb[n - 2]
        val j2 = pb[n - 1]
        var m2P = 0
        var m2B = 0
        for (i in 0 until n - 2) {
            if (pb[i] == j1 && pb[i + 1] == j2) {
                if (pb[i + 2] == "P") m2P++ else m2B++
            }
        }
        val m2Total = m2P + m2B
        if (m2Total >= 2) {
            val pRate2 = (m2P + 1.0) / (m2Total + 2.0)
            val bRate2 = (m2B + 1.0) / (m2Total + 2.0)
            scoreP += pRate2 * 2.0
            scoreB += bRate2 * 2.0
            if (reasons.isEmpty() && m2P != m2B) {
                reasons.add("Nhịp [$j1-$j2] (${max(m2P, m2B)}/$m2Total)")
            }
        }

        // Tầng F: Độ lệch phân phối thực tế của cả bàn (Z-Score)
        val pTotalPb = pb.count { it == "P" }
        val bTotalPb = pb.count { it == "B" }
        val stdDev = sqrt(n * 0.25)
        val zDiff = if (stdDev > 0) abs(pTotalPb - bTotalPb) / stdDev else 0.0
        if (zDiff >= 1.2) {
            if (pTotalPb > bTotalPb) {
                scoreP += 1.2
                if (reasons.isEmpty()) reasons.add("Bàn nghiêng CON ($pTotalPb/$n)")
            } else {
                scoreB += 1.2
                if (reasons.isEmpty()) reasons.add("Bàn nghiêng CÁI ($bTotalPb/$n)")
            }
        }

        val totalScore = scoreP + scoreB
        if (totalScore <= 0.0) {
            return V4ProReport(
                sideMainText = "⏸ CHỜ CẦU (50.0%)",
                sideSubText = subText,
                sideBridgeTag = "Cầu chưa rõ nét • Ăn: $accBadge",
                recommendedSide = "WAIT",
                lastEventText = lastEventLine,
                bridgeDetailText = "Bỏ qua tay nhiễu 50/50 để bảo toàn tỷ lệ thắng",
                winLossText = winLossLine,
                historyTapeText = tapeLine
            )
        }

        // Quy đổi điểm cộng hưởng sang xác suất thực tế (giới hạn tự nhiên trong 50% - 76%, không bao giờ báo ảo 99-100%)
        val rawRatio = max(scoreP, scoreB) / totalScore
        val calibratedProb = (50.0 + (rawRatio - 0.5) * 52.0).coerceIn(50.0, 76.5)
        val chosen = if (scoreP >= scoreB) "P" else "B"
        val bridgeSummary = if (reasons.isNotEmpty()) reasons.take(2).joinToString(" + ") else "Nhịp cầu thực"

        if (calibratedProb < ENTRY_THRESHOLD) {
            val pctStr = String.format(Locale.US, "%.1f%%", calibratedProb)
            return V4ProReport(
                sideMainText = "⏸ CHỜ CẦU ($pctStr)",
                sideSubText = subText,
                sideBridgeTag = "Lọc nhiễu 50/50 • Ăn: $accBadge",
                recommendedSide = "WAIT",
                lastEventText = lastEventLine,
                bridgeDetailText = "Tỷ lệ thật $pctStr chưa đạt ngưỡng chốt ($bridgeSummary)",
                winLossText = winLossLine,
                historyTapeText = tapeLine
            )
        }

        val pctFinal = String.format(Locale.US, "%.1f%%", calibratedProb)
        return if (chosen == "P") {
            V4ProReport(
                sideMainText = "🔵 CON $pctFinal",
                sideSubText = subText,
                sideBridgeTag = "$bridgeSummary • Ăn: $accBadge",
                recommendedSide = "P",
                lastEventText = lastEventLine,
                bridgeDetailText = "Chốt CON ($pctFinal) theo dữ liệu thật: $bridgeSummary",
                winLossText = winLossLine,
                historyTapeText = tapeLine
            )
        } else {
            V4ProReport(
                sideMainText = "🔴 CÁI $pctFinal",
                sideSubText = subText,
                sideBridgeTag = "$bridgeSummary • Ăn: $accBadge",
                recommendedSide = "B",
                lastEventText = lastEventLine,
                bridgeDetailText = "Chốt CÁI ($pctFinal) theo dữ liệu thật: $bridgeSummary",
                winLossText = winLossLine,
                historyTapeText = tapeLine
            )
        }
    }
}
