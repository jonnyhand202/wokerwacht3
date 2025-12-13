package com.workwatch.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telephony Collector - Ağ ve Baz İstasyonu Bilgisi Toplayıcısı
 *
 * Mock GPS tespiti için SÜPER GÜÇLÜ kanıt:
 * GPS koordinatları + Baz İstasyonu bilgisi çelişirse = KESIN HİLE!
 *
 * Örnek:
 * - GPS: İş yeri (41.0082, 28.9784)
 * - Cell Tower: Eve yakın kule (CID: 12345, LAC: 100)
 * → ÇELİŞKİ! Mock GPS tespit edildi! 🚨
 */
@Singleton
class TelephonyCollector @Inject constructor() {

    companion object {
        private const val TAG = "TelephonyCollector"
        private const val UNKNOWN = "Unknown"
    }

    /**
     * Telefon numarasını kullanıcıdan al (Manuel)
     * Otomatik çekmek güvenilir değil
     */
    fun getPhoneNumberFromUser(context: Context): String? {
        // Bu fonksiyon UI tarafından çağrılır
        // Kullanıcı numarayı kendisi girer
        return null // UI'dan gelecek
    }

    /**
     * Baz istasyonu bilgisini topla
     * Check-in/out sırasında çağrılır
     */
    fun collectCellTowerInfo(context: Context): CellTowerInfo {
        if (!hasPermission(context)) {
            android.util.Log.w(TAG, "Location permission not granted")
            return CellTowerInfo.NoPermission
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
            as? TelephonyManager

        if (telephonyManager == null) {
            android.util.Log.e(TAG, "TelephonyManager not available")
            return CellTowerInfo.NotAvailable("TelephonyManager null")
        }

        try {
            // Operatör bilgisi
            val operator = telephonyManager.networkOperatorName ?: UNKNOWN
            val operatorCode = telephonyManager.networkOperator ?: UNKNOWN

            // Network type
            val networkType = getNetworkTypeName(telephonyManager)

            // Signal strength (getAllCellInfo içinde gelecek)

            // Ana bağlı olduğu cell tower
            val cellInfo = getAllCellInfo(telephonyManager)

            if (cellInfo.isEmpty()) {
                android.util.Log.w(TAG, "No cell info available")
                return CellTowerInfo.NotAvailable("No cell info")
            }

            // İlk (en güçlü sinyal) tower'ı al
            val primaryCell = cellInfo.firstOrNull() ?: return CellTowerInfo.NotAvailable("Empty cell list")

            val cellData = parseCellInfo(primaryCell)

            android.util.Log.d(
                TAG,
                "Cell Tower collected - CID: ${cellData.cellId}, LAC: ${cellData.lac}, " +
                "Operator: $operator, Type: $networkType"
            )

            return CellTowerInfo.Available(
                cellId = cellData.cellId,
                lac = cellData.lac,
                mcc = cellData.mcc,
                mnc = cellData.mnc,
                operatorName = operator,
                operatorCode = operatorCode,
                networkType = networkType,
                signalStrength = cellData.signalStrength,
                isRegistered = cellInfo.first().isRegistered,
                timestamp = System.currentTimeMillis()
            )

        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "Security exception", e)
            return CellTowerInfo.NoPermission
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to collect cell tower info", e)
            return CellTowerInfo.NotAvailable("Error: ${e.message}")
        }
    }

    /**
     * Tüm görünen cell tower'ları al
     */
    private fun getAllCellInfo(telephonyManager: TelephonyManager): List<CellInfo> {
        return try {
            telephonyManager.allCellInfo ?: emptyList()
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "Security exception getting all cell info", e)
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get all cell info", e)
            emptyList()
        }
    }

    /**
     * CellInfo'dan veriyi parse et (farklı network tipleri için)
     */
    private fun parseCellInfo(cellInfo: CellInfo): CellData {
        return when (cellInfo) {
            is CellInfoLte -> parseLte(cellInfo)
            is CellInfoGsm -> parseGsm(cellInfo)
            is CellInfoWcdma -> parseWcdma(cellInfo)
            is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                parseNr(cellInfo)
            } else {
                CellData.unknown()
            }
            else -> {
                android.util.Log.w(TAG, "Unknown cell info type: ${cellInfo::class.java.simpleName}")
                CellData.unknown()
            }
        }
    }

    /**
     * LTE (4G) cell info
     */
    private fun parseLte(cellInfo: CellInfoLte): CellData {
        val identity = cellInfo.cellIdentity

        return CellData(
            cellId = identity.ci.toString(),
            lac = identity.tac.toString(),
            mcc = identity.mccString ?: UNKNOWN,
            mnc = identity.mncString ?: UNKNOWN,
            signalStrength = cellInfo.cellSignalStrength.dbm
        )
    }

    /**
     * GSM (2G) cell info
     */
    private fun parseGsm(cellInfo: CellInfoGsm): CellData {
        val identity = cellInfo.cellIdentity

        return CellData(
            cellId = identity.cid.toString(),
            lac = identity.lac.toString(),
            mcc = identity.mccString ?: UNKNOWN,
            mnc = identity.mncString ?: UNKNOWN,
            signalStrength = cellInfo.cellSignalStrength.dbm
        )
    }

    /**
     * WCDMA (3G) cell info
     */
    private fun parseWcdma(cellInfo: CellInfoWcdma): CellData {
        val identity = cellInfo.cellIdentity

        return CellData(
            cellId = identity.cid.toString(),
            lac = identity.lac.toString(),
            mcc = identity.mccString ?: UNKNOWN,
            mnc = identity.mncString ?: UNKNOWN,
            signalStrength = cellInfo.cellSignalStrength.dbm
        )
    }

    /**
     * NR (5G) cell info
     */
    private fun parseNr(cellInfo: CellInfoNr): CellData {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return CellData.unknown()
        }

        val identity = cellInfo.cellIdentity as? CellIdentityNr
            ?: return CellData.unknown()

        return CellData(
            cellId = identity.nci.toString(),
            lac = identity.tac.toString(),
            mcc = identity.mccString ?: UNKNOWN,
            mnc = identity.mncString ?: UNKNOWN,
            signalStrength = (cellInfo.cellSignalStrength as? android.telephony.CellSignalStrengthNr)
                ?.dbm ?: -1
        )
    }

    /**
     * Network type ismini al (2G, 3G, 4G, 5G)
     */
    @Suppress("DEPRECATION")
    private fun getNetworkTypeName(telephonyManager: TelephonyManager): String {
        return try {
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager.dataNetworkType
            } else {
                telephonyManager.networkType
            }

            when (networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN -> "2G"

                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"

                TelephonyManager.NETWORK_TYPE_LTE -> "4G"

                TelephonyManager.NETWORK_TYPE_NR -> "5G"

                else -> "Unknown"
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get network type", e)
            UNKNOWN
        }
    }

    /**
     * Izin kontrolü
     */
    private fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * GPS koordinatları ile Cell Tower location'ı karşılaştır
     * MOCK GPS TESPİTİ!
     */
    fun detectMockGPSWithCellTower(
        gpsLat: Double,
        gpsLon: Double,
        cellTowerInfo: CellTowerInfo.Available
    ): MockGPSDetectionResult {
        // Bu fonksiyon backend'de daha iyi çalışır
        // Çünkü Cell Tower location database'i gerekiyor
        // Burada basit bir kontrol yapabiliriz

        // TODO: OpenCellID gibi bir API kullanarak
        // Cell Tower'ın gerçek koordinatlarını bulup
        // GPS ile karşılaştırabiliriz

        // Şimdilik sadece bilgiyi logla
        android.util.Log.d(
            TAG,
            "GPS: ($gpsLat, $gpsLon), Cell: CID=${cellTowerInfo.cellId}, LAC=${cellTowerInfo.lac}"
        )

        return MockGPSDetectionResult.NeedsBackendValidation(
            gpsCoordinates = Pair(gpsLat, gpsLon),
            cellTowerId = cellTowerInfo.cellId,
            cellTowerLac = cellTowerInfo.lac
        )
    }
}

/**
 * Cell Tower Info - Baz istasyonu bilgisi
 */
sealed class CellTowerInfo {
    data class Available(
        val cellId: String,          // Cell ID (benzersiz kule kimliği)
        val lac: String,             // Location Area Code
        val mcc: String,             // Mobile Country Code
        val mnc: String,             // Mobile Network Code
        val operatorName: String,    // Turkcell, Vodafone, Türk Telekom
        val operatorCode: String,    // 286-01, 286-02, 286-03
        val networkType: String,     // 2G, 3G, 4G, 5G
        val signalStrength: Int,     // dBm (-50 to -120)
        val isRegistered: Boolean,   // Bu tower'a kayıtlı mı?
        val timestamp: Long          // Bilgi toplandığı an
    ) : CellTowerInfo() {

        /**
         * JSON formatında string
         */
        fun toJsonString(): String {
            return """
                {
                  "cellId": "$cellId",
                  "lac": "$lac",
                  "mcc": "$mcc",
                  "mnc": "$mnc",
                  "operator": "$operatorName",
                  "operatorCode": "$operatorCode",
                  "networkType": "$networkType",
                  "signalStrength": $signalStrength,
                  "isRegistered": $isRegistered,
                  "timestamp": $timestamp
                }
            """.trimIndent()
        }

        /**
         * Kısa özet (log için)
         */
        fun toShortString(): String {
            return "CID:$cellId|LAC:$lac|$networkType|$operatorName|${signalStrength}dBm"
        }
    }

    object NoPermission : CellTowerInfo()
    data class NotAvailable(val reason: String) : CellTowerInfo()
}

/**
 * Cell Data - Parse edilmiş cell bilgisi
 */
data class CellData(
    val cellId: String,
    val lac: String,
    val mcc: String,
    val mnc: String,
    val signalStrength: Int
) {
    companion object {
        fun unknown() = CellData(
            cellId = "Unknown",
            lac = "Unknown",
            mcc = "Unknown",
            mnc = "Unknown",
            signalStrength = -1
        )
    }
}

/**
 * Mock GPS Detection Result
 */
sealed class MockGPSDetectionResult {
    data class Suspicious(
        val reason: String,
        val gpsCoordinates: Pair<Double, Double>,
        val cellTowerCoordinates: Pair<Double, Double>,
        val distanceKm: Double
    ) : MockGPSDetectionResult()

    data class NeedsBackendValidation(
        val gpsCoordinates: Pair<Double, Double>,
        val cellTowerId: String,
        val cellTowerLac: String
    ) : MockGPSDetectionResult()

    object LooksLegit : MockGPSDetectionResult()
}
