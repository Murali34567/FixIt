package uk.ac.tees.mad.fixit.domain.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.ac.tees.mad.fixit.data.remote.SupabaseConfig
import uk.ac.tees.mad.fixit.data.model.Result
import java.util.UUID
import java.util.concurrent.TimeUnit

class ImageUploadRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d("Network", "--> ${request.method} ${request.url}")
            val response = chain.proceed(request)
            Log.d("Network", "<-- ${response.code} ${response.message}")
            response
        }
        .build()

    /**
     * Upload image to Supabase storage and return public URL
     */
    suspend fun uploadImage(imageUri: Uri, fileName: String = ""): Flow<Result<String>> = flow {
        try {
            emit(Result.Loading)

            Log.d("ImageUpload", "Starting upload for URI: $imageUri")

            // Generate unique file name if not provided
            val uniqueFileName = fileName.ifBlank {
                "issue_${UUID.randomUUID()}.jpg"
            }

            Log.d("ImageUpload", "Generated filename: $uniqueFileName")

            // Get input stream from URI
            val inputStream = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(imageUri)
            }

            if (inputStream == null) {
                val error = "Failed to read image file from URI: $imageUri"
                Log.e("ImageUpload", error)
                emit(Result.Error(error))
                return@flow
            }

            // Read bytes from input stream
            val imageBytes = inputStream.use { it.readBytes() }
            Log.d("ImageUpload", "Read ${imageBytes.size} bytes from image")

            if (imageBytes.isEmpty()) {
                val error = "Image bytes are empty"
                Log.e("ImageUpload", error)
                emit(Result.Error(error))
                return@flow
            }

            // Create multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    uniqueFileName,
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.STORAGE_BUCKET}/$uniqueFileName")
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                .addHeader("Content-Type", "multipart/form-data")
                .build()

            Log.d("ImageUpload", "Making request to: ${request.url}")

            // Execute network call on IO dispatcher
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            Log.d("ImageUpload", "Response code: ${response.code}")
            Log.d("ImageUpload", "Response message: ${response.message}")

            if (response.isSuccessful) {
                val publicUrl = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/${SupabaseConfig.STORAGE_BUCKET}/$uniqueFileName"
                Log.d("ImageUpload", "✅ Image uploaded successfully: $publicUrl")
                emit(Result.Success(publicUrl))
            } else {
                val errorBody = response.body?.string() ?: "No error body"
                val error = "Upload failed: ${response.code} - $errorBody"
                Log.e("ImageUpload", "❌ $error")
                emit(Result.Error(error))
            }

            response.close()

        } catch (exception: Exception) {
            Log.e("ImageUpload", "❌ Upload exception", exception)
            emit(Result.Error("Failed to upload image: ${exception.message ?: "Unknown error"}", exception))
        }
    }

    /**
     * Delete image from Supabase storage
     */
    suspend fun deleteImage(fileName: String): Flow<Result<Boolean>> = flow {
        try {
            emit(Result.Loading)

            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.STORAGE_BUCKET}/$fileName")
                .delete()
                .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                .build()

            // Execute network call on IO dispatcher
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                emit(Result.Success(true))
                Log.d("ImageUpload", "✅ Image deleted successfully: $fileName")
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                emit(Result.Error("Delete failed: ${response.code} - $errorBody"))
                Log.e("ImageUpload", "❌ Delete failed: ${response.code} - $errorBody")
            }

            response.close()

        } catch (exception: Exception) {
            emit(Result.Error("Failed to delete image: ${exception.message}", exception))
            Log.e("ImageUpload", "❌ Delete exception: ${exception.message}")
        }
    }

    // Test function using direct HTTP
    suspend fun testSupabaseConnection(): Boolean {
        return try {
            val testBytes = "test".toByteArray()
            val testFileName = "test_${System.currentTimeMillis()}.txt"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    testFileName,
                    testBytes.toRequestBody("text/plain".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.STORAGE_BUCKET}/$testFileName")
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                .addHeader("Content-Type", "multipart/form-data")
                .build()

            // Execute network call on IO dispatcher
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val success = response.isSuccessful

            if (success) {
                Log.d("SupabaseTest", "✅ Supabase connection test PASSED")
            } else {
                Log.e("SupabaseTest", "❌ Supabase connection test FAILED: ${response.code}")
            }

            response.close()
            success

        } catch (e: Exception) {
            Log.e("SupabaseTest", "❌ Supabase connection test FAILED: ${e.message}")
            false
        }
    }
}