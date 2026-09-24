package dev.uint.qrserv.net

import dev.uint.qrserv.data.AddressGroup
import dev.uint.qrserv.data.InterfaceAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressChangeTest {

    private val wifi = InterfaceAddress("192.168.2.80", AddressGroup.ROUTABLE)
    private val vpn = InterfaceAddress("10.2.0.2", AddressGroup.ROUTABLE)
    private val loopback = InterfaceAddress("127.0.0.1", AddressGroup.LOOPBACK)

    @Test
    fun staysWithoutSuggestionWhenOnTheBestAddress() {
        assertEquals(AddressChange.Stay(null), addressChange(listOf(wifi, vpn), wifi.address, emptySet()))
    }

    @Test
    fun suggestsABetterAddressInsteadOfMoving() {
        assertEquals(AddressChange.Stay(wifi.address), addressChange(listOf(wifi, vpn), vpn.address, emptySet()))
    }

    @Test
    fun movesToTheBestAddressWhenTheSelectedOneIsGone() {
        assertEquals(AddressChange.MoveTo(vpn), addressChange(listOf(vpn, loopback), wifi.address, emptySet()))
    }

    @Test
    fun movesEvenAfterAManualPickWhenThatAddressIsGone() {
        assertEquals(AddressChange.MoveTo(wifi), addressChange(listOf(wifi), vpn.address, setOf(wifi.address, vpn.address)))
    }

    @Test
    fun manualPickSilencesAddressesThatAlreadyExisted() {
        assertEquals(AddressChange.Stay(null), addressChange(listOf(wifi, vpn), vpn.address, setOf(wifi.address, vpn.address)))
    }

    @Test
    fun manualPickStillAllowsSuggestingANewAddress() {
        val newWifi = InterfaceAddress("192.168.2.133", AddressGroup.ROUTABLE)
        assertEquals(
            AddressChange.Stay(newWifi.address),
            addressChange(listOf(newWifi, vpn), vpn.address, setOf(wifi.address, vpn.address)),
        )
    }

    @Test
    fun emptyListChangesNothing() {
        assertEquals(AddressChange.Stay(null), addressChange(emptyList(), wifi.address, emptySet()))
    }
}
