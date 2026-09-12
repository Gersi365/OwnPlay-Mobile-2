package app.ownplay.mobile.sources.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderTransportFailurePolicyTest {
    @Test
    fun `classifies DNS failures`() {
        assertEquals("NETWORK_DNS", ProviderTransportFailurePolicy.codeFor(UnknownHostException()))
    }

    @Test
    fun `classifies timeouts`() {
        assertEquals("NETWORK_TIMEOUT", ProviderTransportFailurePolicy.codeFor(SocketTimeoutException()))
    }

    @Test
    fun `classifies TLS failures`() {
        assertEquals("TLS", ProviderTransportFailurePolicy.codeFor(SSLException("handshake")))
    }

    @Test
    fun `classifies refused connections`() {
        assertEquals("NETWORK_CONNECT", ProviderTransportFailurePolicy.codeFor(ConnectException()))
    }

    @Test
    fun `classifies Android cleartext blocks without surfacing exception text`() {
        val exception = UnknownServiceException("CLEARTEXT communication to provider.test not permitted by network security policy")

        assertEquals("CLEARTEXT_BLOCKED", ProviderTransportFailurePolicy.codeFor(exception))
    }
}
