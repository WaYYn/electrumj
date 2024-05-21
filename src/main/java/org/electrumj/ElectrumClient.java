package org.electrumj;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonRpcClient;
import org.electrumj.dto.*;
import org.electrumj.dto.transactionget.BlockchainTransactionGetVerboseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ElectrumClient is the central class of this project.
 *
 * Sample usage:
 * <pre>
 *     ElectrumClient client = new ElectrumClient(new ElectrumServer("electrumx-core.1209k.com", 50002, false, ElectrumServer.ServerType.ELECTRUMX));
 *     client.open();
 *     String scripthash = Util.scripthash("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
 *     BlockchainScripthashGetBalanceResponse response = client.blockchainScripthashGetBalance(scripthash);
 *     client.close();
 * </pre>
 */
public class ElectrumClient {

    private static final Logger log = LoggerFactory.getLogger(ElectrumClient.class);

    private ElectrumServer electrumServer;

    // Socket connection to the server.
    private Socket socket;
    // OutputStream to write into to send data to the server.
    private OutputStream socketOutputStream;
    // InputStream to read from to read data from the server.
    private InputStream socketInputStream;

    // Whether the socket connection is already established
    boolean connectionOpened = false;

    // Listener to inform to of new block headers
    private BlockchainHeadersListener blockchainHeadersListener;
    // Listener to inform to of changes of subscribed scripthashes.
    private BlockchainScripthashesListener blockchainScripthashesListener;

    // Section constructors

    /**
     * Creates a new ElectrumClient with the given ElectrumServer object
     * @param electrumServer
     */
    public ElectrumClient(ElectrumServer electrumServer) {
        this.electrumServer = electrumServer;
    }

    // Section get server data

    public ElectrumServer getElectrumServer() {
        return electrumServer;
    }

    // Section connection

    /**
     * Opens the connection to the electrum server.
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public void openConnection() throws GeneralSecurityException, IOException {
        assert !connectionOpened;

        // Überprüfe, ob SSL verwendet werden soll oder nicht
        if (this.electrumServer.isUseSSL()) {
            SSLSocketFactory factory = createTrustAllCertsSocketFactory();
            // Verwende die Factory um eine SSLSocket zu erstellen
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(this.electrumServer.getUrl(), this.electrumServer.getPort());
            sslSocket.startHandshake(); // Starte den SSL-Handshake
            socket = sslSocket; // Weise die SSLSocket der socket-Variable zu
        } else {
            SocketFactory factory = SocketFactory.getDefault();
            // Verwende die Standard SocketFactory für nicht-SSL Verbindungen
            socket = factory.createSocket(this.electrumServer.getUrl(), this.electrumServer.getPort());
        }

        socket.setSoTimeout(90000); // Setze einen Timeout von 90 Sekunden

        // Initialisiere die Streams, um Daten zu senden und zu empfangen
        socketOutputStream = new AppendNewLineOutputStream(socket.getOutputStream());
        socketInputStream = socket.getInputStream();
        SocketChannel channel = socket.getChannel(); // Optional, falls du direkten Zugriff auf den SocketChannel benötigst
        connectionOpened = true; // Markiere die Verbindung als geöffnet
    }

    /**
     * Closes the connection to the electrum server.
     * @throws IOException
     */
    public void closeConnection() throws IOException {
        assert connectionOpened;
        socketInputStream.close();
        socketOutputStream.close();
        socket.close();
    }

    /**
     * Creates a SSLSocketFactory that ignore certificate chain validation because Electrum servers use mostly
     * self signed certificates.
     */
    private static SSLSocketFactory createTrustAllCertsSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
        };
        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc.getSocketFactory();
    }

    /**
     * Creates a SSLSocketFactory that does certificate chain validation.
     * Currently not being used.
     */
    private static SSLSocketFactory createSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        return (SSLSocketFactory)SSLSocketFactory.getDefault();
    }

    // Section notifications

    /**
     * Sets the blockchainHeadersListener
     * @param blockchainHeadersListener
     */
    public void setBlockchainHeadersListener(BlockchainHeadersListener blockchainHeadersListener) {
        this.blockchainHeadersListener = blockchainHeadersListener;
    }

    /**
     * Removes the blockchainHeadersListener
     */
    public void cleanBlockchainHeadersListener() {
        this.blockchainHeadersListener = null;
    }

    /**
     * Sets the blockchainScripthashesListener
     * @param blockchainScripthashesListener
     */
    public void setBlockchainScripthashesListener(BlockchainScripthashesListener blockchainScripthashesListener) {
        this.blockchainScripthashesListener = blockchainScripthashesListener;
    }

    /**
     * Removes the blockchainScripthashesListener
     */
    public void cleanBlockchainScripthashesListener() {
        this.blockchainScripthashesListener = null;
    }

    /**
     * Starts a new thread that reads the socket input stream and invokes the associated listener when
     * the server sends notifications.
     */
    public void listenNotifications() {
        new Thread() {
            @Override
            public void run() {
                BufferedReader in = new BufferedReader(new InputStreamReader(socketInputStream));
                while (true) {
                    try {
                        String notificationJsonString = in.readLine();
                        if (notificationJsonString == null) break;
                        log.debug("Received notification: " + notificationJsonString);
                        ObjectMapper mapper = new ObjectMapper();
                        Map notificationMap = mapper.readValue(notificationJsonString, Map.class);
                        if (notificationMap.get("method").equals("blockchain.headers.subscribe")) {
                            Map blockchainHeadersSubscribeResponeMap = (Map) ((List)notificationMap.get("params")).get(0);
                            BlockchainHeader header = new BlockchainHeader(blockchainHeadersSubscribeResponeMap);
                            blockchainHeadersListener.notifyNewBlockchainHeader(header);
                        } else if (notificationMap.get("method").equals("blockchain.scripthash.subscribe")) {
                            List blockchainScripthashNotificationList = (List) notificationMap.get("params");
                            BlockchainScripthashStatus status = new BlockchainScripthashStatus(blockchainScripthashNotificationList);
                            blockchainScripthashesListener.notifyNewScripthashStatus(status);
                        } else {
                            throw new IllegalArgumentException("Unrecognized notification: " + notificationJsonString);
                        }
                    } catch (Exception e) {
                        log.warn(e.getMessage(), e);
                    }
                }
            }
        }.start();
    }

    // Section requests

    /**
     * Sends a "blockchain.block.header" request to the server with cp_height hardcoded to 0.
     * @param height
     * @return
     * @throws Throwable
     */
    public String blockchainBlockHeader(long height) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(height, 0) : Map.of("height", height, "cp_height", 0);
        String response = doRequest("blockchain.block.header", params, String.class);
        return response;
    }

    /**
     * Sends a "blockchain.block.header" request to the server.
     * @param height
     * @param cpHeight
     * @return
     * @throws Throwable
     */
    public BlockchainBlockHeaderWithProofResponse blockchainBlockHeader(long height, long cpHeight) throws Throwable {
        assert cpHeight > 0;
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(height, cpHeight) : Map.of("height", height, "cp_height", cpHeight);
        BlockchainBlockHeaderWithProofResponse response = doRequest("blockchain.block.header", params, BlockchainBlockHeaderWithProofResponse.class);
        return response;
    }

    /**
     * Sends a "blockchain.block.headers" request to the server.
     * @param startHeight
     * @param count
     * @param cpHeight
     * @return
     * @throws Throwable
     */
    public BlockchainBlockHeadersResponse blockchainBlockHeaders(long startHeight, long count, long cpHeight) throws Throwable {
        assert cpHeight > 0;
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(startHeight, count, cpHeight) : Map.of("start_height", startHeight, "count", count, "cp_height", cpHeight);
        BlockchainBlockHeadersResponse response = doRequest("blockchain.block.headers", params, BlockchainBlockHeadersResponse.class);
        return response;
    }

    /**
     * Sends a "blockchain.estimatefee" request to the server.
     * @param targetNumberOfBlocks
     * @return
     * @throws Throwable
     */
    public double blockchainEstimatefee(long targetNumberOfBlocks) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(targetNumberOfBlocks) : Map.of("number", targetNumberOfBlocks);
        double response = doRequest("blockchain.estimatefee", params, Double.class);
        return response;
    }

    /**
     * Sends a "blockchain.headers.subscribe" request to the server.
     * @return
     * @throws Throwable
     */
    public BlockchainHeader blockchainHeadersSubscribe() throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of();
        BlockchainHeader response = doRequest("blockchain.headers.subscribe", params, BlockchainHeader.class);
        return response;
    }

    /**
     * Sends a "blockchain.relayfee" request to the server.
     * @return
     * @throws Throwable
     */
    public double blockchainRelayfee() throws Throwable {
        double response = doRequest("blockchain.relayfee", electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of(), Double.class);
        return response;
    }

    /**
     * Sends a "blockchain.scripthash.get_balance" request to the server.
     * @param scripthash
     * @return
     * @throws Throwable
     */
    public BlockchainScripthashGetBalanceResponse blockchainScripthashGetBalance(String scripthash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(scripthash) : Map.of("scripthash", scripthash);
        BlockchainScripthashGetBalanceResponse response = doRequest("blockchain.scripthash.get_balance", params, BlockchainScripthashGetBalanceResponse.class);
        log.debug("Received balance response: " + response);
        return response;
    }

    /**
     * Sends a "blockchain.scripthash.get_history" request to the server.
     * @param scripthash
     * @return
     * @throws Throwable
     */
    public List<BlockchainScripthashGetTxsResponseEntry> blockchainScripthashGetHistory(String scripthash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(scripthash) : Map.of("scripthash", scripthash);
        Type returnType = Util.getParametrizedListType(BlockchainScripthashGetTxsResponseEntry.class);
        List<BlockchainScripthashGetTxsResponseEntry> response = (List<BlockchainScripthashGetTxsResponseEntry>) doRequest("blockchain.scripthash.get_history", params, returnType);
        return response;
    }

    /**
     * Sends a "blockchain.scripthash.get_mempool" request to the server.
     * @param scripthash
     * @return
     * @throws Throwable
     */
    public List<BlockchainScripthashGetTxsResponseEntry> blockchainScripthashGetMempool(String scripthash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(scripthash) : Map.of("scripthash", scripthash);
        Type returnType = Util.getParametrizedListType(BlockchainScripthashGetTxsResponseEntry.class);
        List<BlockchainScripthashGetTxsResponseEntry> response = (List<BlockchainScripthashGetTxsResponseEntry>) doRequest("blockchain.scripthash.get_mempool", params, returnType);
        return response;
    }

    /**
     * Sends a "blockchain.scripthash.listunspent" request to the server.
     * @param scripthash
     * @return
     * @throws Throwable
     */
    public List<BlockchainScripthashListUnspentResponseEntry> blockchainScripthashListUnspent(String scripthash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(scripthash) : Map.of("scripthash", scripthash);
        Type returnType = Util.getParametrizedListType(BlockchainScripthashListUnspentResponseEntry.class);
        List<BlockchainScripthashListUnspentResponseEntry> response = (List<BlockchainScripthashListUnspentResponseEntry>) doRequest("blockchain.scripthash.listunspent", params, returnType);
        return response;
    }

    /**
     * Sends a "blockchain.scripthash.subscribe" request to the server.
     * @param scripthash
     * @return
     * @throws Throwable
     */
    public String blockchainScripthashSubscribe(String scripthash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(scripthash) : Map.of("scripthash", scripthash);
        String response = doRequest("blockchain.scripthash.subscribe", params, String.class);
        return response;
    }

    /**
     * Sends a "blockchain.scripthash.unsubscribe" request to the server.
     * @param scripthash
     * @return
     * @throws Throwable
     */
    public boolean blockchainScripthashUnsubscribe(String scripthash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(scripthash) : Map.of("scripthash", scripthash);
        boolean response = doRequest("blockchain.scripthash.unsubscribe", params, Boolean.class);
        return response;
    }

    /**
     * Sends a "blockchain.transaction.broadcast" request to the server.
     * @param rawTx
     * @return
     * @throws Throwable
     */
    public String blockchainTransactionBroadcast(String rawTx) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(rawTx) : Map.of("raw_tx", rawTx);
        String response = doRequest("blockchain.transaction.broadcast", params, String.class);
        return response;
    }

    /**
     * Sends a "blockchain.transaction.get" request to the server with verbose set to false.
     * @param txHash
     * @return
     * @throws Throwable
     */
    public String blockchainTransactionGetNoVerbose(String txHash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(txHash, false) : Map.of("tx_hash", txHash, "verbose", false);
        String response = doRequest("blockchain.transaction.get", params, String.class);
        return response;
    }

    /**
     * Sends a "blockchain.transaction.get" request to the server with verbose set to true.
     * @param txHash
     * @return
     * @throws Throwable
     */
    public BlockchainTransactionGetVerboseResponse blockchainTransactionGetVerbose(String txHash) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(txHash, true) : Map.of("tx_hash", txHash, "verbose", true);
        BlockchainTransactionGetVerboseResponse response = doRequest("blockchain.transaction.get", params, BlockchainTransactionGetVerboseResponse.class);
        return response;
    }

    /**
     * Sends a "blockchain.transaction.get_merkle" request to the server.
     * @param txHash
     * @param height
     * @return
     * @throws Throwable
     */
    public BlockchainTransactionGetMerkleResponse blockchainTransactionGetMerkle(String txHash, long height) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(txHash, height) : Map.of("tx_hash", txHash, "height", height);
        BlockchainTransactionGetMerkleResponse response = doRequest("blockchain.transaction.get_merkle", params, BlockchainTransactionGetMerkleResponse.class);
        return response;
    }

    /**
     * Sends a "blockchain.transaction.id_from_pos" request to the server with merkle set to false.
     * @param height
     * @param txPos
     * @return
     * @throws Throwable
     */
    public String blockchainTransactionIdFromPosNoMerkle(long height, long txPos) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(height, txPos, false) : Map.of("height", height, "tx_pos", txPos, "merkle", false);
        String response = doRequest("blockchain.transaction.id_from_pos", params, String.class);
        return response;
    }

    /**
     * Sends a "blockchain.transaction.id_from_pos" request to the server with merkle set to true.
     * @param height
     * @param txPos
     * @return
     * @throws Throwable
     */
    public BlockchainTransactionIdFromPosMerkleResponse blockchainTransactionIdFromPosMerkle(long height, long txPos) throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(height, txPos, true) : Map.of("height", height, "tx_pos", txPos, "merkle", true);
        BlockchainTransactionIdFromPosMerkleResponse response = doRequest("blockchain.transaction.id_from_pos", params, BlockchainTransactionIdFromPosMerkleResponse.class);
        return response;
    }

    /**
     * Sends a "mempool.get_fee_histogram" request to the server.
     * @return
     * @throws Throwable
     */
    public List<MempoolGetFeeHistogramResponseEntry> mempoolGetFeeHistogram() throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of();
        Type returnType = Util.getParametrizedListType(MempoolGetFeeHistogramResponseEntry.class);
        List<MempoolGetFeeHistogramResponseEntry> response = (List<MempoolGetFeeHistogramResponseEntry>) doRequest("mempool.get_fee_histogram", params, returnType);
        return response;
    }

    /**
     * Sends a "server.banner" request to the server.
     * @return
     * @throws Throwable
     */
    public String serverBanner() throws Throwable {
        String response = doRequest("server.banner", electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of(), String.class);
        return response;
    }

    /**
     * Sends a "server.donation_address" request to the server.
     * @return
     * @throws Throwable
     */
    public String serverDonationAddress() throws Throwable {
        String response = doRequest("server.donation_address", electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of(), String.class);
        return response;
    }

    /**
     * Sends a "server.features" request to the server.
     * @return
     * @throws Throwable
     */
    public ServerFeaturesResponse serverFeatures() throws Throwable {
        ServerFeaturesResponse response = doRequest("server.features", electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of(), ServerFeaturesResponse.class);
        return response;
    }

    /**
     * Sends a "server.peers.subscribe" request to the server.
     * @return
     * @throws Throwable
     */
    public List<ServerPeersSubscribeResponseEntry> serverPeersSubscribe() throws Throwable {
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of();
        Type returnType = Util.getParametrizedListType(ServerPeersSubscribeResponseEntry.class);
        List<ServerPeersSubscribeResponseEntry> response = (List<ServerPeersSubscribeResponseEntry>) doRequest("server.peers.subscribe", params, returnType);
        return response;
    }

    /**
     * Sends a "server.ping" request to the server.
     * @throws Throwable
     */
    public void serverPing() throws Throwable {
        doRequest("server.ping", electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of(), Object.class);
    }

    /**
     * Sends a "server.version" request to the server.
     * @return
     * @throws Throwable
     */
    public ServerVersionResponse serverVersion() throws Throwable {
        String clientName = "electrumj 0.1-SNAPSHOT";
        String protocolVersion = "1.4.2";
        Object params = electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of(clientName, protocolVersion) : Map.of("client_name", clientName, "protocol_version", protocolVersion);
        ServerVersionResponse response = doRequest("server.version", params, ServerVersionResponse.class);
        return response;
    }

    // Section request execution

    private <T> T doRequest(String method, Class<T> returnType) throws Throwable {
        return doRequest(method, electrumServer.getServerType() == ElectrumServer.ServerType.ELECTRS ? List.of() : Map.of(), returnType);
    }

    private <T> T doRequest(String method, Object params, Class<T> returnType) throws Throwable {
        Object result = doRequest(method, params, Type.class.cast(returnType));
        return (T) result;
    }

    private Object doRequest(String method, Object params, Type returnType) throws Throwable {
        assert connectionOpened;
        JsonRpcClient client = new JsonRpcClient();
        Object result = client.invokeAndReadResponse(method, params, returnType,
                this.socketOutputStream,
                this.socketInputStream);
        return result;
    }
}
