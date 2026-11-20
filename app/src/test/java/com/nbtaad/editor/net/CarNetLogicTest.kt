package com.nbtaad.editor.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarNetLogicTest {
    @Test
    fun theSubnetIsTheFirstThreeParts() {
        assertEquals("192.168.0", CarNetLogic.subnetOf("192.168.0.13"))
        assertEquals("172.16.224", CarNetLogic.subnetOf("172.16.224.112"))
    }

    @Test
    fun aWifiNameComesWithoutAndroidsQuotes() {
        assertEquals("Dongle", CarNetLogic.cleanSsid("\"Dongle\""))
        assertEquals("Dongle 5G", CarNetLogic.cleanSsid(" Dongle 5G "))
    }

    @Test
    fun whatIsNotAWifiNameIsNull() {
        assertNull(CarNetLogic.cleanSsid("<unknown ssid>"))
        assertNull(CarNetLogic.cleanSsid("0x"))
        assertNull(CarNetLogic.cleanSsid(""))
        assertNull(CarNetLogic.cleanSsid("\"\""))
        assertNull(CarNetLogic.cleanSsid(null))
    }

    @Test
    fun theCarNetworkIsToldFromTheHotspotOfTheDaemon() {
        assertTrue(CarNetLogic.onSubnet("192.168.0.13", "192.168.0"))
        assertFalse(CarNetLogic.onSubnet("172.16.224.112", "192.168.0"))
        assertFalse(CarNetLogic.onSubnet("192.168.0.13", ""))
    }
}
