# electrumj

Java client for the electrum protocol.

### Sample usage

```
    ElectrumClient client = new ElectrumClient(PublicElectrumServers.ELECTRS_TCP__blockstream);
    client.openConnection();
    String scripthash = Util.scripthash("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
    BlockchainScripthashGetBalanceResponse response = client.blockchainScripthashGetBalance(scripthash);
    client.closeConnection();
    System.out.println("Confirmed: " + response.getConfirmed());
    System.out.println("Unconfirmed: " + response.getUnconfirmed());
```

Should print something like this:

```
Confirmed: 4997842536
Unconfirmed: 7135
```

See `ElectrumClientTest` for more examples.

Declaring electrumj as a gradle dependency:

```
repositories {
    ...
    maven { url 'https://jitpack.io' }
}

dependencies {
    ...
    implementation 'com.github.WaYYn:electrumj:v0.2'
}
```

### Useful resouces

[Protocol documentation](https://electrumx-spesmilo.readthedocs.io/en/latest/protocol-methods.html)
`<br/>`

Used libraries:`<br/>`
[jsonrpc4j](https://github.com/briandilley/jsonrpc4j) `<br/>`
[jackson](https://github.com/FasterXML/jackson)
`<br/>`

Additional resources:`<br/>`
[Electrum documentation](https://electrum.readthedocs.io/en/latest/faq.html) `<br/>`
[electrum source code](https://github.com/spesmilo/electrum) `<br/>`
[Electrumx documentation](https://electrumx-spesmilo.readthedocs.io/en/latest/) `<br/>`
[electrumx source code](https://github.com/spesmilo/electrumx) `<br/>`
[Electrum go client](https://github.com/checksum0/go-electrum)
