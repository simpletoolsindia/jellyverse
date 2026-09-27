package com.sridhar.harbor.data.jellyfin.admin

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Jellyfin 10.9+ administration endpoints (the web dashboard's API surface). Requires an administrator token. */
interface JellyfinAdminApi {
    // ---- Server ----
    @GET("System/Info") suspend fun systemInfo(): SystemInfo
    @POST("System/Restart") suspend fun restart(): Response<ResponseBody>
    @POST("System/Shutdown") suspend fun shutdown(): Response<ResponseBody>
    @GET("Items/Counts") suspend fun counts(): ItemCounts
    @GET("System/Logs") suspend fun logs(): List<LogFile>
    @Streaming @GET("System/Logs/Log") suspend fun log(@Query("name") name: String): ResponseBody

    // ---- Users ----
    @GET("Users") suspend fun users(): List<AdminUser>
    @GET("Users/{id}") suspend fun userRaw(@Path("id") id: String): JsonObject
    @POST("Users/New") suspend fun createUser(@Body body: NewUser): AdminUser
    @DELETE("Users/{id}") suspend fun deleteUser(@Path("id") id: String): Response<ResponseBody>
    @POST("Users") suspend fun updateUser(@Query("userId") id: String, @Body user: JsonObject): Response<ResponseBody>
    @POST("Users/{id}/Policy") suspend fun updatePolicy(@Path("id") id: String, @Body policy: JsonObject): Response<ResponseBody>
    @POST("Users/Password") suspend fun updatePassword(@Query("userId") id: String, @Body body: PasswordChange): Response<ResponseBody>

    // ---- Sessions / remote control ----
    @GET("Sessions") suspend fun sessions(@Query("activeWithinSeconds") activeWithin: Int = 960): List<AdminSession>
    @POST("Sessions/{id}/Playing/{command}")
    suspend fun playCommand(@Path("id") id: String, @Path("command") command: String, @Query("seekPositionTicks") seekTicks: Long? = null): Response<ResponseBody>
    @POST("Sessions/{id}/Message") suspend fun message(@Path("id") id: String, @Body body: SessionMessage): Response<ResponseBody>

    // ---- Libraries ----
    @GET("Library/VirtualFolders") suspend fun libraries(): List<LibraryFolder>
    @POST("Library/Refresh") suspend fun scanAll(): Response<ResponseBody>
    @POST("Items/{id}/Refresh")
    suspend fun scanLibrary(
        @Path("id") id: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("MetadataRefreshMode") meta: String = "Default",
        @Query("ImageRefreshMode") images: String = "Default",
        @Query("ReplaceAllMetadata") replaceMeta: Boolean = false,
        @Query("ReplaceAllImages") replaceImages: Boolean = false,
    ): Response<ResponseBody>
    @POST("Library/VirtualFolders")
    suspend fun addLibrary(
        @Query("name") name: String,
        @Query("collectionType") type: String,
        @Query("paths") paths: List<String>,
        @Query("refreshLibrary") refresh: Boolean = true,
        @Body body: JsonObject = JsonObject(mapOf("LibraryOptions" to JsonObject(emptyMap()))),
    ): Response<ResponseBody>
    @DELETE("Library/VirtualFolders") suspend fun removeLibrary(@Query("name") name: String, @Query("refreshLibrary") refresh: Boolean = true): Response<ResponseBody>

    // ---- Media ----
    @DELETE("Items/{id}") suspend fun deleteItem(@Path("id") id: String): Response<ResponseBody>

    // ---- Scheduled tasks ----
    @GET("ScheduledTasks") suspend fun tasks(@Query("isHidden") hidden: Boolean = false): List<ScheduledTask>
    @POST("ScheduledTasks/Running/{id}") suspend fun startTask(@Path("id") id: String): Response<ResponseBody>
    @DELETE("ScheduledTasks/Running/{id}") suspend fun stopTask(@Path("id") id: String): Response<ResponseBody>

    // ---- Activity, devices, plugins, API keys ----
    @GET("System/ActivityLog/Entries") suspend fun activity(@Query("startIndex") start: Int = 0, @Query("limit") limit: Int = 50): ActivityPage
    @GET("Devices") suspend fun devices(): DevicePage
    @DELETE("Devices") suspend fun deleteDevice(@Query("id") id: String): Response<ResponseBody>
    @GET("Plugins") suspend fun plugins(): List<PluginInfo>
    @POST("Plugins/{id}/{version}/Enable") suspend fun enablePlugin(@Path("id") id: String, @Path("version") version: String): Response<ResponseBody>
    @POST("Plugins/{id}/{version}/Disable") suspend fun disablePlugin(@Path("id") id: String, @Path("version") version: String): Response<ResponseBody>
    @POST("Packages/Installed/{name}") suspend fun installPackage(@Path("name") name: String): Response<ResponseBody>
    @GET("Auth/Keys") suspend fun apiKeys(): ApiKeyPage
    @POST("Auth/Keys") suspend fun createApiKey(@Query("app") app: String): Response<ResponseBody>
    @DELETE("Auth/Keys/{key}") suspend fun revokeApiKey(@Path("key") key: String): Response<ResponseBody>
}
