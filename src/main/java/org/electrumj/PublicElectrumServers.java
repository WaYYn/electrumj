package org.electrumj;

/**
 * Hardcoded URLs of some known public electrum servers. Updated: 05/2024
 */
public class PublicElectrumServers {

    // ElectrumX tcp (unsupported)
    public static final ElectrumServer ELECTRUMX_TCP__xtrum = new ElectrumServer("xtrum.com",50001,false, ElectrumServer.ServerType.ELECTRUMX);
    public static final ElectrumServer ELECTRUMX_TCP__coineuskal = new ElectrumServer("electrum.coineuskal.com",50001,false, ElectrumServer.ServerType.ELECTRUMX);

    // ElectrumX ssl
    public static final ElectrumServer ELECTRUMX_SSL__xtrum = new ElectrumServer("xtrum.com",50002,true, ElectrumServer.ServerType.ELECTRUMX);
    public static final ElectrumServer ELECTRUMX_SSL__qtornado = new ElectrumServer("fortress.qtornado.com", 50002, true, ElectrumServer.ServerType.ELECTRUMX);
    public static final ElectrumServer ELECTRUMX_SSL__coineuskal = new ElectrumServer("electrum.coineuskal.com",50002,true, ElectrumServer.ServerType.ELECTRUMX);


    // Electrs tcp
    public static final ElectrumServer ELECTRS_TCP__ocf = new ElectrumServer("btc.ocf.sh", 50001, false, ElectrumServer.ServerType.ELECTRS);
    public static final ElectrumServer ELECTRS_TCP__blockstream = new ElectrumServer("electrum.blockstream.info", 50001, false, ElectrumServer.ServerType.ELECTRS);

    // Electrs ssl
    public static final ElectrumServer ELECTRS_SSL__tjader = new ElectrumServer("electrum.tjader.xyz", 50002, true, ElectrumServer.ServerType.ELECTRS);
    public static final ElectrumServer ELECTRS_SSL__getsrt = new ElectrumServer("electrum.getsrt.net", 50002, true, ElectrumServer.ServerType.ELECTRS);


    // Fluctrum tcp (unsupported)
    public static final ElectrumServer FLUCTRUM__TCP__grey =new ElectrumServer("bitcoin.grey.pw",50001,false, ElectrumServer.ServerType.ELECTRS);

    // Fluctrum ssl (unsupported)
    public static final ElectrumServer FLUCTRUM__SSL__grey =new ElectrumServer("bitcoin.grey.pw",50002,true, ElectrumServer.ServerType.ELECTRS);

}
