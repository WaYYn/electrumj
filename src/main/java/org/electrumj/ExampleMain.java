package org.electrumj;

import org.electrumj.dto.BlockchainScripthashGetBalanceResponse;

public class ExampleMain {
    public static void main(String[] args) throws Throwable {
        ElectrumClient client = new ElectrumClient(PublicElectrumServers.ELECTRS_TCP__blockstream);
        client.openConnection();
        String scripthash = Util.scripthash("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
        BlockchainScripthashGetBalanceResponse response = client.blockchainScripthashGetBalance(scripthash);
        client.closeConnection();
        System.out.println("Confirmed: " + response.getConfirmed());
        System.out.println("Unconfirmed: " + response.getUnconfirmed());
    }
}
