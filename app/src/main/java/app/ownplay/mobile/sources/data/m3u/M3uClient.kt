package app.ownplay.mobile.sources.data.m3u

interface M3uClient {
    suspend fun fetch(remoteLocator: String): M3uResult<String>
}
