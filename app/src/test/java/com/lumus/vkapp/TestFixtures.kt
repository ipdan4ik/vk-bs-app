package com.lumus.vkapp

val sampleWireGuardConfig = """
    [Interface]
    Address = 192.0.2.2/32
    DNS = 1.1.1.1
    PrivateKey = TFlmmEUC7V7VtiDYLKsbP5rySTKLIZq1yn8lMqK83wo=

    [Peer]
    AllowedIPs = 0.0.0.0/0
    Endpoint = 192.0.2.1:51820
    PersistentKeepalive = 0
    PublicKey = vBN7qyUTb5lJtWYJ8LhbPio1Z4RcyBPGnqFBGn6O6Qg=
""".trimIndent()

val sampleWireGuardConfigWithIncludedApps = """
    [Interface]
    Address = 192.0.2.2/32
    DNS = 1.1.1.1
    IncludedApplications = com.lumus.vkapp, com.example.browser
    PrivateKey = TFlmmEUC7V7VtiDYLKsbP5rySTKLIZq1yn8lMqK83wo=

    [Peer]
    AllowedIPs = 0.0.0.0/0
    Endpoint = 192.0.2.1:51820
    PersistentKeepalive = 0
    PublicKey = vBN7qyUTb5lJtWYJ8LhbPio1Z4RcyBPGnqFBGn6O6Qg=
""".trimIndent()
