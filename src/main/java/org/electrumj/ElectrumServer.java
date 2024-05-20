package org.electrumj;

public class ElectrumServer {

    public enum ServerType {
        ELECTRUMX, ELECTRS
    }

    private String url;
    private int port;
    private boolean useSSL;
    private ServerType serverType;

    public ElectrumServer(String url, int port, boolean useSSL, ServerType serverType) {
        this.url = url;
        this.port = port;
        this.useSSL = useSSL;
        this.serverType = serverType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isUseSSL() {
        return useSSL;
    }

    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }

    public ServerType getServerType() {
        return serverType;
    }

    public void setServerType(ServerType serverType) {
        this.serverType = serverType;
    }

    @Override
    public String toString() {
        return "ElectrumServer{" +
                "url='" + url + '\'' +
                ", port=" + port +
                ", useSSL=" + useSSL +
                ", serverType=" + serverType +
                '}';
    }
}
