package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class TuyaCloudTest {
    @Test
    fun `token request is signed like the Tuya reference`() {
        assertEquals(
            "006E057FAC81BD9DA6A6197D53E3D8F17C84B9F7E6FBCCF24D6704CECD0DBC60",
            tuyaSign("client-id", "secret", "", "1700000000000", "GET", "/v1.0/token?grant_type=1", "")
        )
    }

    @Test
    fun `command with token and body is signed like the Tuya reference`() {
        assertEquals(
            "472C9441D185CBDCD5FEDB04C4B7BC1560B8BDE046978A23159278EA7EF169EA",
            tuyaSign(
                "client-id", "secret", "token", "1700000000000", "POST", "/v1.0/iot-03/devices/abc/commands",
                """{"commands":[{"code":"switch_1","value":true}]}"""
            )
        )
    }

    @Test
    fun `plugs are parsed in order and broken entries skipped`() {
        assertEquals(
            listOf(Plug("Pal", "id1"), Plug("Bet", "id2")),
            parsePlugs("Pal:id1, Bet : id2 ,broken,:id3,Sch:")
        )
        assertEquals(emptyList<Plug>(), parsePlugs(""))
    }
}
