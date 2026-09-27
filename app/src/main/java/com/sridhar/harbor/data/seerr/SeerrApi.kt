package com.sridhar.harbor.data.seerr

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SeerrApi {
    @GET("api/v1/status") suspend fun status(): SeerrStatus
    @GET("api/v1/auth/me") suspend fun me(): SeerrUser

    @GET("api/v1/discover/trending")
    suspend fun trending(@Query("page") page: Int = 1): PageResult<SeerrMedia>
    @GET("api/v1/discover/movies")
    suspend fun popularMovies(@Query("page") page: Int = 1): PageResult<SeerrMedia>
    @GET("api/v1/discover/tv")
    suspend fun popularTv(@Query("page") page: Int = 1): PageResult<SeerrMedia>
    @GET("api/v1/discover/movies/upcoming")
    suspend fun upcomingMovies(@Query("page") page: Int = 1): PageResult<SeerrMedia>
    @GET("api/v1/discover/tv/upcoming")
    suspend fun upcomingTv(@Query("page") page: Int = 1): PageResult<SeerrMedia>

    @GET("api/v1/search")
    suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1): PageResult<SeerrMedia>

    @GET("api/v1/movie/{id}") suspend fun movie(@Path("id") id: Int): SeerrDetails
    @GET("api/v1/tv/{id}") suspend fun tv(@Path("id") id: Int): SeerrDetails
    @GET("api/v1/movie/{id}/recommendations")
    suspend fun movieRecs(@Path("id") id: Int): PageResult<SeerrMedia>
    @GET("api/v1/tv/{id}/recommendations")
    suspend fun tvRecs(@Path("id") id: Int): PageResult<SeerrMedia>

    @GET("api/v1/request")
    suspend fun requests(
        @Query("take") take: Int = 20,
        @Query("skip") skip: Int = 0,
        @Query("filter") filter: String = "all",
        @Query("sort") sort: String = "added",
        @Query("requestedBy") requestedBy: Int? = null,
    ): RequestPage

    @GET("api/v1/request/count") suspend fun requestCount(): RequestCount
    @POST("api/v1/request") suspend fun request(@Body body: NewRequest): SeerrRequest
    @POST("api/v1/request/{id}/{status}")
    suspend fun setRequestStatus(@Path("id") id: Int, @Path("status") status: String): Response<ResponseBody>
    @DELETE("api/v1/request/{id}") suspend fun deleteRequest(@Path("id") id: Int): Response<ResponseBody>
    @POST("api/v1/request/{id}/retry") suspend fun retryRequest(@Path("id") id: Int): Response<ResponseBody>

    @GET("api/v1/user")
    suspend fun users(@Query("take") take: Int = 100, @Query("skip") skip: Int = 0, @Query("sort") sort: String = "created"): UserPage
    @GET("api/v1/user/{id}") suspend fun user(@Path("id") id: Int): SeerrUser
    @POST("api/v1/user/{id}/settings/permissions")
    suspend fun setPermissions(@Path("id") id: Int, @Body body: PermissionsBody): Response<ResponseBody>
    @DELETE("api/v1/user/{id}") suspend fun deleteUser(@Path("id") id: Int): Response<ResponseBody>
    @GET("api/v1/settings/jellyfin/users") suspend fun jellyfinUsers(): List<JellyfinImportUser>
    @POST("api/v1/user/import-from-jellyfin")
    suspend fun importJellyfinUsers(@Body body: ImportUsersBody): Response<ResponseBody>
}
