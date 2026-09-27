package com.sridhar.harbor.data.jellyfin

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

private const val DEFAULT_FIELDS =
    "Overview,Genres,PrimaryImageAspectRatio,MediaSourceCount,ChildCount,Taglines,PremiereDate,RemoteTrailers"

interface JellyfinApi {
    @GET("QuickConnect/Enabled") suspend fun quickConnectEnabled(): Boolean
    @POST("QuickConnect/Initiate") suspend fun quickConnectInitiate(): QuickConnectResult
    @GET("QuickConnect/Connect") suspend fun quickConnectState(@Query("secret") secret: String): QuickConnectResult
    @POST("Users/AuthenticateWithQuickConnect") suspend fun authenticateWithQuickConnect(@Body body: QuickConnectAuth): AuthResult
    @POST("QuickConnect/Authorize") suspend fun quickConnectAuthorize(@Query("code") code: String, @Query("userId") userId: String?): Response<ResponseBody>

    @GET("Items")
    suspend fun libraryItems(
        @Query("userId") userId: String,
        @Query("includeItemTypes") types: String = "Movie,Series",
        @Query("recursive") recursive: Boolean = true,
        @Query("fields") fields: String = "ProviderIds,Path",
        @Query("limit") limit: Int = 5000,
    ): LibraryItemsResult

    @POST("Items/RemoteSearch/{kind}")
    suspend fun remoteSearch(@Path("kind") kind: String, @Body body: RemoteSearchQuery): List<kotlinx.serialization.json.JsonObject>

    @POST("Items/RemoteSearch/Apply/{id}")
    suspend fun applySearchResult(
        @Path("id") id: String,
        @Body body: kotlinx.serialization.json.JsonObject,
        @Query("replaceAllImages") replaceImages: Boolean = true,
    ): Response<ResponseBody>

    @POST("Items/{id}/Refresh")
    suspend fun refreshItem(
        @Path("id") id: String,
        @Query("metadataRefreshMode") meta: String = "FullRefresh",
        @Query("imageRefreshMode") images: String = "FullRefresh",
        @Query("replaceAllMetadata") replaceMeta: Boolean = true,
        @Query("replaceAllImages") replaceImages: Boolean = true,
    ): Response<ResponseBody>

    @POST("Library/Refresh")
    suspend fun refreshLibrary(): Response<ResponseBody>

    @GET("Library/VirtualFolders")
    suspend fun virtualFolders(): List<VirtualFolder>

    @POST("Users/AuthenticateByName")
    suspend fun authenticate(@Body body: AuthRequest): AuthResult

    @GET("Users/{id}")
    suspend fun user(@Path("id") id: String): JfUser

    @GET("UserViews")
    suspend fun views(@Query("userId") userId: String): ItemsResult

    @GET("UserItems/Resume")
    suspend fun resume(
        @Query("userId") userId: String,
        @Query("mediaTypes") mediaTypes: String = "Video",
        @Query("limit") limit: Int = 20,
        @Query("fields") fields: String = DEFAULT_FIELDS,
        @Query("enableImageTypes") imageTypes: String = "Primary,Backdrop,Thumb",
    ): ItemsResult

    @GET("Shows/NextUp")
    suspend fun nextUp(
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 20,
        @Query("seriesId") seriesId: String? = null,
        @Query("fields") fields: String = DEFAULT_FIELDS,
    ): ItemsResult

    @GET("Items/Latest")
    suspend fun latest(
        @Query("userId") userId: String,
        @Query("parentId") parentId: String,
        @Query("limit") limit: Int = 20,
        @Query("fields") fields: String = DEFAULT_FIELDS,
    ): List<BaseItem>

    @GET("Items")
    suspend fun items(
        @Query("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") types: String? = null,
        @Query("recursive") recursive: Boolean = true,
        @Query("sortBy") sortBy: String = "SortName",
        @Query("sortOrder") sortOrder: String = "Ascending",
        @Query("startIndex") start: Int = 0,
        @Query("limit") limit: Int = 60,
        @Query("searchTerm") search: String? = null,
        @Query("filters") filters: String? = null,
        @Query("genres") genres: String? = null,
        @Query("years") years: String? = null,
        @Query("fields") fields: String = DEFAULT_FIELDS,
        @Query("imageTypeLimit") imageTypeLimit: Int = 1,
        @Query("ids") ids: String? = null,
    ): ItemsResult

    /** Subtitle providers configured on the server (e.g. the Open Subtitles plugin). */
    @GET("Items/{id}/RemoteSearch/Subtitles/{language}")
    suspend fun searchSubtitles(@Path("id") id: String, @Path("language") language: String, @Query("isPerfectMatch") perfect: Boolean = false): List<RemoteSubtitle>

    /** Server downloads the subtitle, saves it next to the media and adds it as an external stream. */
    @POST("Items/{id}/RemoteSearch/Subtitles/{subtitleId}")
    suspend fun downloadSubtitle(@Path("id") id: String, @Path("subtitleId") subtitleId: String): Response<ResponseBody>

    /** Lightweight whole-library scan for the language index (no images, audio streams only). */
    @GET("Items")
    suspend fun indexItems(
        @Query("userId") userId: String,
        @Query("parentId") parentId: String?,
        @Query("includeItemTypes") types: String,
        @Query("recursive") recursive: Boolean = true,
        @Query("fields") fields: String = "MediaStreams,Genres,DateCreated,SortName,PremiereDate",
        @Query("enableImages") enableImages: Boolean = false,
    ): IndexResult

    @GET("Items/{id}")
    suspend fun item(
        @Path("id") id: String,
        @Query("userId") userId: String,
        @Query("fields") fields: String = "Trickplay,Chapters,MediaSources,People,Overview,Genres,Taglines,MediaStreams",
    ): BaseItem

    @GET("Items/{id}/Similar")
    suspend fun similar(
        @Path("id") id: String,
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 16,
    ): ItemsResult

    @GET("Shows/{id}/Seasons")
    suspend fun seasons(@Path("id") seriesId: String, @Query("userId") userId: String): ItemsResult

    @GET("Shows/{id}/Episodes")
    suspend fun episodes(
        @Path("id") seriesId: String,
        @Query("userId") userId: String,
        @Query("seasonId") seasonId: String? = null,
        @Query("fields") fields: String = "Overview,MediaSources",
    ): ItemsResult

    @GET("MediaSegments/{id}")
    suspend fun segments(@Path("id") id: String): MediaSegmentsResult

    @POST("Sessions/Playing")
    suspend fun playing(@Body body: PlaybackReport): Response<ResponseBody>

    @POST("Sessions/Playing/Progress")
    suspend fun progress(@Body body: PlaybackReport): Response<ResponseBody>

    @POST("Sessions/Playing/Stopped")
    suspend fun stopped(@Body body: PlaybackReport): Response<ResponseBody>

    @POST("UserPlayedItems/{id}")
    suspend fun markPlayed(@Path("id") id: String, @Query("userId") userId: String): Response<ResponseBody>

    @DELETE("UserPlayedItems/{id}")
    suspend fun markUnplayed(@Path("id") id: String, @Query("userId") userId: String): Response<ResponseBody>

    @POST("UserFavoriteItems/{id}")
    suspend fun favorite(@Path("id") id: String, @Query("userId") userId: String): Response<ResponseBody>

    @DELETE("UserFavoriteItems/{id}")
    suspend fun unfavorite(@Path("id") id: String, @Query("userId") userId: String): Response<ResponseBody>
}
