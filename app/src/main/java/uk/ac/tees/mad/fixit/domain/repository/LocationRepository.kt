package uk.ac.tees.mad.fixit.domain.repository

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.tasks.await
import uk.ac.tees.mad.fixit.data.model.IssueLocation
import java.io.IOException
import java.util.Locale

class LocationRepository(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Get current location using network provider (approximate location)
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getCurrentLocation(): Result<IssueLocation> {
        return try {
            // Check if location permissions are granted (simplified check)
            // In production, you should properly check permissions before calling this

            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                object : CancellationToken() {
                    override fun onCanceledRequested(listener: OnTokenCanceledListener) =
                        CancellationTokenSource().token

                    override fun isCancellationRequested() = false
                }
            ).await()

            if (location != null) {
                val address = getAddressFromLocation(location.latitude, location.longitude)
                Result.success(
                    IssueLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address
                    )
                )
            } else {
                Result.failure(Exception("Unable to get current location"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get address from coordinates using reverse geocoding
     */
    suspend fun getAddressFromLocation(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())

            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
            }

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val addressParts = mutableListOf<String>()

                // Build address string from available parts
                address.thoroughfare?.let { addressParts.add(it) } // Street
                address.subLocality?.let { addressParts.add(it) } // Area
                address.locality?.let { addressParts.add(it) } // City
                address.postalCode?.let { addressParts.add(it) } // Postal code
                address.countryName?.let { addressParts.add(it) } // Country

                if (addressParts.isNotEmpty()) {
                    addressParts.joinToString(", ")
                } else {
                    "Address not available"
                }
            } else {
                "Address not found"
            }
        } catch (e: IOException) {
            "Unable to get address: ${e.message}"
        } catch (e: Exception) {
            "Error getting address: ${e.message}"
        }
    }

    /**
     * Get last known location (faster but might be outdated)
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getLastKnownLocation(): Result<IssueLocation> {
        return try {
            val location = fusedLocationClient.lastLocation.await()

            if (location != null) {
                val address = getAddressFromLocation(location.latitude, location.longitude)
                Result.success(
                    IssueLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address
                    )
                )
            } else {
                Result.failure(Exception("No last known location available"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}